var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
import { TransportChannelId } from "../api_bindings.js";
import { showErrorPopup } from "../component/error.js";
import { buildAudioPipeline } from "./audio/pipeline.js";
import { BIG_BUFFER } from "./buffer.js";
import { defaultStreamInputConfig, StreamInput } from "./input.js";
import { Logger } from "./log.js";
import { gatherPipeInfo, getPipe } from "./pipeline/index.js";
import { StreamStats } from "./stats.js";
import { WebSocketTransport } from "./transport/web_socket.js";
import { WebRTCTransport } from "./transport/webrtc.js";
import { allVideoCodecs, andVideoCodecs, createSupportedVideoFormatsBits, emptyVideoCodecs, getSelectedVideoCodec, hasAnyCodec } from "./video.js";
import { buildVideoPipeline } from "./video/pipeline.js";
export function getStreamerSize(settings, viewerScreenSize) {
    let width, height;
    if (settings.videoSize == "720p") {
        width = 1280;
        height = 720;
    }
    else if (settings.videoSize == "1080p") {
        width = 1920;
        height = 1080;
    }
    else if (settings.videoSize == "1440p") {
        width = 2560;
        height = 1440;
    }
    else if (settings.videoSize == "4k") {
        width = 3840;
        height = 2160;
    }
    else if (settings.videoSize == "custom") {
        width = settings.videoSizeCustom.width;
        height = settings.videoSizeCustom.height;
    }
    else { // native
        width = viewerScreenSize[0];
        height = viewerScreenSize[1];
    }
    return [width, height];
}
function getVideoCodecHint(settings) {
    let videoCodecHint = emptyVideoCodecs();
    if (settings.videoCodec == "h264") {
        videoCodecHint.H264 = true;
        videoCodecHint.H264_HIGH8_444 = true;
    }
    else if (settings.videoCodec == "h265") {
        videoCodecHint.H265 = true;
        videoCodecHint.H265_MAIN10 = true;
        videoCodecHint.H265_REXT8_444 = true;
        videoCodecHint.H265_REXT10_444 = true;
    }
    else if (settings.videoCodec == "av1") {
        videoCodecHint.AV1 = true;
        videoCodecHint.AV1_MAIN8 = true;
        videoCodecHint.AV1_MAIN10 = true;
        videoCodecHint.AV1_REXT8_444 = true;
        videoCodecHint.AV1_REXT10_444 = true;
    }
    else if (settings.videoCodec == "auto") {
        videoCodecHint = allVideoCodecs();
    }
    return videoCodecHint;
}
export class Stream {
    constructor(api, hostId, appId, settings, viewerScreenSize) {
        this.logger = new Logger();
        this.divElement = document.createElement("div");
        this.eventTarget = new EventTarget();
        this.iceServers = null;
        this.videoRenderer = null;
        this.audioPlayer = null;
        this.transport = null;
        // -- Raw Web Socket stuff
        this.wsSendBuffer = [];
        this.logger.addInfoListener((info, type) => {
            this.debugLog(info, { type: type !== null && type !== void 0 ? type : undefined });
        });
        this.api = api;
        this.hostId = hostId;
        this.appId = appId;
        this.settings = settings;
        this.streamerSize = getStreamerSize(settings, viewerScreenSize);
        // Configure web socket
        const wsApiHost = api.host_url.replace(/^http(s)?:/, "ws$1:");
        // TODO: firstly try out WebTransport
        this.ws = new WebSocket(`${wsApiHost}/host/stream`);
        this.ws.addEventListener("error", this.onError.bind(this));
        this.ws.addEventListener("open", this.onWsOpen.bind(this));
        this.ws.addEventListener("close", this.onWsClose.bind(this));
        this.ws.addEventListener("message", this.onRawWsMessage.bind(this));
        this.sendWsMessage({
            Init: {
                host_id: this.hostId,
                app_id: this.appId,
                video_frame_queue_size: this.settings.videoFrameQueueSize,
                audio_sample_queue_size: this.settings.audioSampleQueueSize,
            }
        });
        // Stream Input
        const streamInputConfig = defaultStreamInputConfig();
        Object.assign(streamInputConfig, {
            mouseScrollMode: this.settings.mouseScrollMode,
            controllerConfig: this.settings.controllerConfig
        });
        this.input = new StreamInput(streamInputConfig);
        // Stream Stats
        this.stats = new StreamStats();
    }
    debugLog(message, additional) {
        for (const line of message.split("\n")) {
            const event = new CustomEvent("stream-info", {
                detail: { type: "addDebugLine", line, additional }
            });
            this.eventTarget.dispatchEvent(event);
        }
    }
    onMessage(message) {
        return __awaiter(this, void 0, void 0, function* () {
            var _a, _b, _c, _d;
            if ("DebugLog" in message) {
                const debugLog = message.DebugLog;
                this.debugLog(debugLog.message, {
                    type: (_a = debugLog.ty) !== null && _a !== void 0 ? _a : undefined
                });
            }
            else if ("UpdateApp" in message) {
                const event = new CustomEvent("stream-info", {
                    detail: { type: "app", app: message.UpdateApp.app }
                });
                this.eventTarget.dispatchEvent(event);
            }
            else if ("ConnectionComplete" in message) {
                const capabilities = message.ConnectionComplete.capabilities;
                const formatRaw = message.ConnectionComplete.format;
                const width = message.ConnectionComplete.width;
                const height = message.ConnectionComplete.height;
                const fps = message.ConnectionComplete.fps;
                const audioSampleRate = message.ConnectionComplete.audio_sample_rate;
                const audioChannelCount = message.ConnectionComplete.audio_channel_count;
                const audioStreams = message.ConnectionComplete.audio_streams;
                const audioCoupledStreams = message.ConnectionComplete.audio_coupled_streams;
                const audioSamplesPerFrame = message.ConnectionComplete.audio_samples_per_frame;
                const audioMapping = message.ConnectionComplete.audio_mapping;
                const format = getSelectedVideoCodec(formatRaw);
                if (format == null) {
                    this.debugLog(`Video Format ${formatRaw} was not found! Couldn't start stream!`, { type: "fatal" });
                    return;
                }
                const event = new CustomEvent("stream-info", {
                    detail: { type: "connectionComplete", capabilities }
                });
                this.eventTarget.dispatchEvent(event);
                this.input.onStreamStart(capabilities, [width, height]);
                this.stats.setVideoInfo(format !== null && format !== void 0 ? format : "Unknown", width, height, fps);
                // we should allow streaming without audio
                if (!this.audioPlayer) {
                    showErrorPopup("Failed to find supported audio player -> audio is missing.");
                }
                yield Promise.all([
                    (_b = this.videoRenderer) === null || _b === void 0 ? void 0 : _b.setup({
                        codec: format,
                        fps,
                        width,
                        height,
                    }),
                    (_c = this.audioPlayer) === null || _c === void 0 ? void 0 : _c.setup({
                        sampleRate: audioSampleRate,
                        channels: audioChannelCount,
                        streams: audioStreams,
                        coupledStreams: audioCoupledStreams,
                        samplesPerFrame: audioSamplesPerFrame,
                        mapping: audioMapping,
                    })
                ]);
            }
            else if ("ConnectionTerminated" in message) {
                const code = message.ConnectionTerminated.error_code;
                this.debugLog(`ConnectionTerminated with code ${code}`, { type: "fatalDescription" });
            }
            // -- WebRTC Config
            else if ("Setup" in message) {
                const iceServers = message.Setup.ice_servers;
                this.iceServers = iceServers;
                this.debugLog(`Using WebRTC Ice Servers: ${createPrettyList(iceServers.map(server => server.urls).reduce((list, url) => list.concat(url), []))}`);
                yield this.startConnection();
            }
            // -- WebRTC
            else if ("WebRtc" in message) {
                const webrtcMessage = message.WebRtc;
                if (this.transport instanceof WebRTCTransport) {
                    this.transport.onReceiveMessage(webrtcMessage);
                }
                else {
                    this.debugLog(`Received WebRTC message but transport is currently ${(_d = this.transport) === null || _d === void 0 ? void 0 : _d.implementationName}`);
                }
            }
        });
    }
    startConnection() {
        return __awaiter(this, void 0, void 0, function* () {
            this.debugLog(`Using transport: ${this.settings.dataTransport}`);
            if (this.settings.dataTransport == "auto") {
                let shutdownReason = yield this.tryWebRTCTransport();
                if (shutdownReason == "failednoconnect") {
                    this.debugLog("Failed to establish WebRTC connection. Falling back to Web Socket transport.");
                    yield this.tryWebSocketTransport();
                }
            }
            else if (this.settings.dataTransport == "webrtc") {
                yield this.tryWebRTCTransport();
            }
            else if (this.settings.dataTransport == "websocket") {
                yield this.tryWebSocketTransport();
            }
            this.debugLog("Tried all configured transport options but no connection was possible", { type: "fatal" });
        });
    }
    setTransport(transport) {
        if (this.transport) {
            this.transport.close();
        }
        this.transport = transport;
        this.input.setTransport(this.transport);
        this.stats.setTransport(this.transport);
    }
    tryWebRTCTransport() {
        return __awaiter(this, void 0, void 0, function* () {
            var _a;
            this.debugLog("Trying WebRTC transport");
            this.sendWsMessage({
                SetTransport: "WebRTC"
            });
            if (!this.iceServers) {
                this.debugLog(`Failed to try WebRTC Transport: no ice servers available`);
                return "failednoconnect";
            }
            const transport = new WebRTCTransport(this.logger);
            transport.onsendmessage = (message) => this.sendWsMessage({ WebRtc: message });
            transport.initPeer({
                iceServers: this.iceServers
            });
            this.setTransport(transport);
            // Wait for negotiation
            const result = yield (new Promise((resolve, _reject) => {
                transport.onconnect = () => resolve(true);
                transport.onclose = () => resolve(false);
            }));
            this.debugLog(`WebRTC negotiation success: ${result}`);
            if (!result) {
                return "failednoconnect";
            }
            // Print pipe support
            const pipesInfo = yield gatherPipeInfo();
            this.logger.debug(`Supported Pipes: {`);
            let isFirst = true;
            for (const [key, value] of pipesInfo.entries()) {
                this.logger.debug(`${isFirst ? "" : ","}"${(_a = getPipe(key)) === null || _a === void 0 ? void 0 : _a.name}": ${JSON.stringify(value)}`);
                isFirst = false;
            }
            this.logger.debug(`}`);
            const videoCodecSupport = yield this.createPipelines();
            if (!videoCodecSupport) {
                this.debugLog("No video pipeline was found for the codec that was specified. If you're unsure which codecs are supported use H264.", { type: "fatalDescription" });
                yield transport.close();
                return "failednoconnect";
            }
            yield this.startStream(videoCodecSupport);
            return new Promise((resolve, reject) => {
                transport.onclose = (shutdown) => {
                    resolve(shutdown);
                };
            });
        });
    }
    tryWebSocketTransport() {
        return __awaiter(this, void 0, void 0, function* () {
            this.debugLog("Trying Web Socket transport");
            this.sendWsMessage({
                SetTransport: "WebSocket"
            });
            const transport = new WebSocketTransport(this.ws, BIG_BUFFER, this.logger);
            this.setTransport(transport);
            const videoCodecSupport = yield this.createPipelines();
            if (!videoCodecSupport) {
                this.debugLog("Failed to start stream because no video pipeline with support for the specified codec was found!", { type: "fatal" });
                return;
            }
            yield this.startStream(videoCodecSupport);
            return new Promise((resolve, reject) => {
                transport.onclose = (shutdown) => {
                    resolve(shutdown);
                };
            });
        });
    }
    createPipelines() {
        return __awaiter(this, void 0, void 0, function* () {
            var _a, _b, _c, _d;
            // Print supported pipes
            const pipesInfo = yield gatherPipeInfo();
            this.logger.debug(`Supported Pipes: {`);
            let isFirst = true;
            for (const [pipe, info] of pipesInfo) {
                this.logger.debug(`${isFirst ? "" : ","}"${pipe.name}": ${JSON.stringify(info)}`);
                isFirst = false;
            }
            this.logger.debug(`}`);
            // Create pipelines
            const [supportedVideoCodecs] = yield Promise.all([this.createVideoRenderer(), this.createAudioPlayer()]);
            const videoPipeline = `${(_a = this.transport) === null || _a === void 0 ? void 0 : _a.getChannel(TransportChannelId.HOST_VIDEO).type} (transport) -> ${(_b = this.videoRenderer) === null || _b === void 0 ? void 0 : _b.implementationName} (renderer)`;
            this.debugLog(`Using video pipeline: ${videoPipeline}`);
            const audioPipeline = `${(_c = this.transport) === null || _c === void 0 ? void 0 : _c.getChannel(TransportChannelId.HOST_AUDIO).type} (transport) -> ${(_d = this.audioPlayer) === null || _d === void 0 ? void 0 : _d.implementationName} (player)`;
            this.debugLog(`Using audio pipeline: ${audioPipeline}`);
            this.stats.setVideoPipelineName(videoPipeline);
            this.stats.setAudioPipelineName(audioPipeline);
            return supportedVideoCodecs;
        });
    }
    createVideoRenderer() {
        return __awaiter(this, void 0, void 0, function* () {
            if (this.videoRenderer) {
                this.debugLog("Found an old video renderer -> cleaning it up");
                this.videoRenderer.unmount(this.divElement);
                this.videoRenderer.cleanup();
                this.videoRenderer = null;
            }
            if (!this.transport) {
                this.debugLog("Failed to setup video without transport");
                return null;
            }
            const codecHint = getVideoCodecHint(this.settings);
            this.debugLog(`Codec Hint by the user: ${JSON.stringify(codecHint)}`);
            if (!hasAnyCodec(codecHint)) {
                this.debugLog("Couldn't find any supported video format. Change the codec option to H264 in the settings if you're unsure which codecs are supported.", { type: "fatalDescription" });
                return null;
            }
            const transportCodecSupport = yield this.transport.setupHostVideo({
                type: ["videotrack", "data"]
            });
            this.debugLog(`Transport supports these video codecs: ${JSON.stringify(transportCodecSupport)}`);
            const videoSettings = {
                supportedVideoCodecs: andVideoCodecs(codecHint, transportCodecSupport),
                canvasRenderer: this.settings.canvasRenderer,
                forceVideoElementRenderer: this.settings.forceVideoElementRenderer
            };
            let pipelineCodecSupport;
            const video = this.transport.getChannel(TransportChannelId.HOST_VIDEO);
            if (video.type == "videotrack") {
                const { videoRenderer, supportedCodecs, error } = yield buildVideoPipeline("videotrack", videoSettings, this.logger);
                if (error) {
                    return null;
                }
                pipelineCodecSupport = supportedCodecs;
                videoRenderer.mount(this.divElement);
                video.addTrackListener((track) => {
                    videoRenderer.setTrack(track);
                });
                this.videoRenderer = videoRenderer;
            }
            else if (video.type == "data") {
                const { videoRenderer, supportedCodecs, error } = yield buildVideoPipeline("data", videoSettings, this.logger);
                if (error) {
                    return null;
                }
                pipelineCodecSupport = supportedCodecs;
                videoRenderer.mount(this.divElement);
                video.addReceiveListener((data) => {
                    videoRenderer.submitPacket(data);
                });
                this.videoRenderer = videoRenderer;
            }
            else {
                this.debugLog(`Failed to create video pipeline with transport channel of type ${video.type} (${this.transport.implementationName})`);
                return null;
            }
            return pipelineCodecSupport;
        });
    }
    createAudioPlayer() {
        return __awaiter(this, void 0, void 0, function* () {
            var _a;
            if (this.audioPlayer) {
                this.debugLog("Found an old audio player -> cleaning it up");
                this.audioPlayer.unmount(this.divElement);
                this.audioPlayer.cleanup();
                this.audioPlayer = null;
            }
            if (!this.transport) {
                this.debugLog("Failed to setup audio without transport");
                return false;
            }
            this.transport.setupHostAudio({
                type: ["audiotrack", "data"]
            });
            const audio = (_a = this.transport) === null || _a === void 0 ? void 0 : _a.getChannel(TransportChannelId.HOST_AUDIO);
            if (audio.type == "audiotrack") {
                const { audioPlayer, error } = yield buildAudioPipeline("audiotrack", this.settings, this.logger);
                if (error) {
                    return false;
                }
                audioPlayer.mount(this.divElement);
                audio.addTrackListener((track) => audioPlayer.setTrack(track));
                this.audioPlayer = audioPlayer;
            }
            else if (audio.type == "data") {
                const { audioPlayer, error } = yield buildAudioPipeline("data", this.settings, this.logger);
                if (error) {
                    return false;
                }
                audioPlayer.mount(this.divElement);
                audio.addReceiveListener((data) => {
                    audioPlayer.decodeAndPlay({
                        // TODO: fill in duration and timestamp
                        durationMicroseconds: 0,
                        timestampMicroseconds: 0,
                        data
                    });
                });
                this.audioPlayer = audioPlayer;
            }
            else {
                this.debugLog(`Cannot find audio pipeline for transport type "${audio.type}"`);
                return false;
            }
            return true;
        });
    }
    startStream(videoCodecSupport) {
        return __awaiter(this, void 0, void 0, function* () {
            const message = {
                StartStream: {
                    bitrate: this.settings.bitrate,
                    packet_size: this.settings.packetSize,
                    fps: this.settings.fps,
                    width: this.streamerSize[0],
                    height: this.streamerSize[1],
                    play_audio_local: this.settings.playAudioLocal,
                    video_supported_formats: createSupportedVideoFormatsBits(videoCodecSupport),
                    video_colorspace: "Rec709",
                    video_color_range_full: false,
                }
            };
            this.debugLog(`Starting stream with info: ${JSON.stringify(message)}`);
            this.debugLog(`Stream video codec info: ${JSON.stringify(videoCodecSupport)}`);
            this.sendWsMessage(message);
        });
    }
    mount(parent) {
        parent.appendChild(this.divElement);
    }
    unmount(parent) {
        parent.removeChild(this.divElement);
    }
    getVideoRenderer() {
        return this.videoRenderer;
    }
    getAudioPlayer() {
        return this.audioPlayer;
    }
    onWsOpen() {
        this.debugLog(`Web Socket Open`);
        for (const raw of this.wsSendBuffer.splice(0)) {
            this.ws.send(raw);
        }
    }
    onWsClose() {
        this.debugLog(`Web Socket Closed`);
    }
    onError(event) {
        this.debugLog(`Web Socket or WebRtcPeer Error`);
        console.error(`Web Socket or WebRtcPeer Error`, event);
    }
    sendWsMessage(message) {
        const raw = JSON.stringify(message);
        if (this.ws.readyState == WebSocket.OPEN) {
            this.ws.send(raw);
        }
        else {
            this.wsSendBuffer.push(raw);
        }
    }
    onRawWsMessage(event) {
        const message = event.data;
        if (typeof message == "string") {
            const json = JSON.parse(message);
            this.onMessage(json);
        }
    }
    // -- Class Api
    addInfoListener(listener) {
        this.eventTarget.addEventListener("stream-info", listener);
    }
    removeInfoListener(listener) {
        this.eventTarget.removeEventListener("stream-info", listener);
    }
    getInput() {
        return this.input;
    }
    getStats() {
        return this.stats;
    }
    getStreamerSize() {
        return this.streamerSize;
    }
}
function createPrettyList(list) {
    let isFirst = true;
    let text = "[";
    for (const item of list) {
        if (!isFirst) {
            text += ", ";
        }
        isFirst = false;
        text += item;
    }
    text += "]";
    return text;
}
