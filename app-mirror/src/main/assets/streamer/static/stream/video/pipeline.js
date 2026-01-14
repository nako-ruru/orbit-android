var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
import { CanvasVideoRenderer } from "./canvas.js";
import { VideoElementRenderer } from "./video_element.js";
import { VideoMediaStreamTrackProcessorPipe } from "./media_stream_track_processor_pipe.js";
import { VideoDecoderPipe } from "./video_decoder_pipe.js";
import { DepacketizeVideoPipe } from "./depackitize_video_pipe.js";
import { VideoMediaStreamTrackGeneratorPipe } from "./media_stream_track_generator_pipe.js";
import { andVideoCodecs, hasAnyCodec } from "../video.js";
import { buildPipeline, gatherPipeInfo } from "../pipeline/index.js";
import { workerPipe } from "../pipeline/worker_pipe.js";
import { WorkerDataSendPipe, WorkerVideoFrameReceivePipe, WorkerVideoTrackReceivePipe, WorkerVideoTrackSendPipe } from "../pipeline/worker_io.js";
import { OffscreenCanvasVideoRenderer } from "./offscreen_canvas.js";
const VIDEO_RENDERERS = [
    VideoElementRenderer,
    CanvasVideoRenderer,
    OffscreenCanvasVideoRenderer,
];
export const WorkerVideoMediaStreamProcessorPipe = workerPipe("WorkerVideoMediaStreamProcessorPipe", { pipes: ["WorkerVideoTrackReceivePipe", "VideoMediaStreamTrackProcessorPipe", "WorkerVideoFrameSendPipe"] });
export const WorkerVideoMediaStreamProcessorCanvasPipe = workerPipe("WorkerVideoMediaStreamProcessorCanvasPipe", { pipes: ["WorkerVideoTrackReceivePipe", "VideoMediaStreamTrackProcessorPipe", "WorkerOffscreenCanvasSendPipe"] });
export const WorkerDataToVideoTrackPipe = workerPipe("WorkerVideoFrameToTrackPipe", { pipes: ["WorkerDataReceivePipe", "DepacketizeVideoPipe", "VideoDecoderPipe", "VideoTrackGeneratorPipe", "WorkerVideoTrackSendPipe"] });
const FORCE_CANVAS_PIPELINES = [
    // -- track
    // Convert track -> video frame -> canvas, Chromium
    { input: "videotrack", pipes: [VideoMediaStreamTrackProcessorPipe], renderer: CanvasVideoRenderer },
    // Convert track -> video frame (in worker) -> canvas (in worker), Safari
    { input: "videotrack", pipes: [WorkerVideoTrackSendPipe, WorkerVideoMediaStreamProcessorCanvasPipe], renderer: OffscreenCanvasVideoRenderer },
    // Convert track -> video frame (in worker) -> canvas, Safari
    { input: "videotrack", pipes: [WorkerVideoTrackSendPipe, WorkerVideoMediaStreamProcessorPipe, WorkerVideoFrameReceivePipe], renderer: CanvasVideoRenderer },
    // -- data
    // Convert data -> video frame -> canvas, Default (should be supported everywhere)
    { input: "data", pipes: [DepacketizeVideoPipe, VideoDecoderPipe], renderer: CanvasVideoRenderer },
];
const PIPELINES = [
    // -- track
    // Convert track -> video element, Default (should be supported everywhere)
    { input: "videotrack", pipes: [], renderer: VideoElementRenderer },
    // Convert track -> video frame -> canvas, Chromium
    { input: "videotrack", pipes: [VideoMediaStreamTrackProcessorPipe], renderer: CanvasVideoRenderer },
    // Convert track -> video frame (in worker) -> canvas (in worker), Safari
    { input: "videotrack", pipes: [WorkerVideoTrackSendPipe, WorkerVideoMediaStreamProcessorCanvasPipe], renderer: OffscreenCanvasVideoRenderer },
    // Convert track -> video frame (in worker) -> canvas, Safari
    { input: "videotrack", pipes: [WorkerVideoTrackSendPipe, WorkerVideoMediaStreamProcessorPipe, WorkerVideoFrameReceivePipe], renderer: CanvasVideoRenderer },
    // -- data
    // Convert data -> video frame (in worker) -> track (in worker, VideoTrackGenerator) -> video element, Safari
    { input: "data", pipes: [WorkerDataSendPipe, WorkerDataToVideoTrackPipe, WorkerVideoTrackReceivePipe], renderer: VideoElementRenderer },
    // Convert data -> video frame -> track (MediaStreamTrackGenerator) -> video element, Chromium
    { input: "data", pipes: [DepacketizeVideoPipe, VideoDecoderPipe, VideoMediaStreamTrackGeneratorPipe], renderer: VideoElementRenderer },
    // Convert data -> video frame -> canvas, Firefox / Fallback
    { input: "data", pipes: [DepacketizeVideoPipe, VideoDecoderPipe], renderer: CanvasVideoRenderer },
];
export function buildVideoPipeline(type, settings, logger) {
    return __awaiter(this, void 0, void 0, function* () {
        const pipesInfo = yield gatherPipeInfo();
        if (logger) {
            // Print supported pipes
            const videoRendererInfoPromises = [];
            for (const videoRenderer of VIDEO_RENDERERS) {
                videoRendererInfoPromises.push(videoRenderer.getInfo().then(info => [videoRenderer.name, info]));
            }
            const videoRendererInfo = yield Promise.all(videoRendererInfoPromises);
            logger.debug(`Supported Video Renderers: {`);
            let isFirst = true;
            for (const [name, info] of videoRendererInfo) {
                logger.debug(`${isFirst ? "" : ","}"${name}": ${JSON.stringify(info)}`);
                isFirst = false;
            }
            logger.debug(`}`);
        }
        logger === null || logger === void 0 ? void 0 : logger.debug(`Building video pipeline with output "${type}"`);
        let pipelines = [];
        // Forced renderer
        if (settings.forceVideoElementRenderer) {
            logger === null || logger === void 0 ? void 0 : logger.debug("Forcing Video Element Renderer");
            if (type != "videotrack") {
                logger === null || logger === void 0 ? void 0 : logger.debug("The option Force Video Element Renderer is currently only supported with WebRTC", { type: "fatalDescription" });
                return { videoRenderer: null, supportedCodecs: null, error: true };
            }
            // H264 is assumed universal, if we don't currently support something force it!
            if (!hasAnyCodec(settings.supportedVideoCodecs)) {
                logger === null || logger === void 0 ? void 0 : logger.debug("No codec currently found. Setting H264 as supported even though the browser says it is not supported");
                settings.supportedVideoCodecs.H264 = true;
            }
            return { videoRenderer: new VideoElementRenderer(), supportedCodecs: settings.supportedVideoCodecs, error: false };
        }
        if (settings.canvasRenderer) {
            logger === null || logger === void 0 ? void 0 : logger.debug("Forcing canvas renderer");
            pipelines = FORCE_CANVAS_PIPELINES;
        }
        else {
            logger === null || logger === void 0 ? void 0 : logger.debug("Selecting pipeline automatically");
            pipelines = PIPELINES;
        }
        pipelineLoop: for (const pipeline of pipelines) {
            if (pipeline.input != type) {
                continue;
            }
            // Check if supported and contains codecs
            let supportedCodecs = settings.supportedVideoCodecs;
            for (const pipe of pipeline.pipes) {
                const pipeInfo = pipesInfo.get(pipe);
                if (!pipeInfo) {
                    logger === null || logger === void 0 ? void 0 : logger.debug(`Failed to query info for video pipe ${pipe.name}`);
                    continue pipelineLoop;
                }
                if (!pipeInfo.environmentSupported) {
                    continue pipelineLoop;
                }
                if (pipeInfo.supportedVideoCodecs) {
                    supportedCodecs = andVideoCodecs(supportedCodecs, pipeInfo.supportedVideoCodecs);
                }
            }
            const rendererInfo = yield pipeline.renderer.getInfo();
            if (!rendererInfo) {
                logger === null || logger === void 0 ? void 0 : logger.debug(`Failed to query info for video renderer ${pipeline.renderer.name}`);
                continue pipelineLoop;
            }
            if (!rendererInfo.environmentSupported) {
                continue pipelineLoop;
            }
            if (rendererInfo.supportedVideoCodecs) {
                supportedCodecs = andVideoCodecs(supportedCodecs, rendererInfo.supportedVideoCodecs);
            }
            if (!hasAnyCodec(supportedCodecs)) {
                logger === null || logger === void 0 ? void 0 : logger.debug(`Not using pipe ${pipeline.pipes.map(pipe => pipe.name).join(" -> ")} -> ${pipeline.renderer.name} (renderer) because it doesn't support any codec the user wants`);
                continue pipelineLoop;
            }
            // Build that pipeline
            const videoRenderer = buildPipeline(pipeline.renderer, { pipes: pipeline.pipes }, logger);
            if (!videoRenderer) {
                logger === null || logger === void 0 ? void 0 : logger.debug("Failed to build video pipeline");
                return { videoRenderer: null, supportedCodecs: null, error: true };
            }
            return { videoRenderer: videoRenderer, supportedCodecs, error: false };
        }
        logger === null || logger === void 0 ? void 0 : logger.debug("No supported video renderer found!");
        return { videoRenderer: null, supportedCodecs: null, error: true };
    });
}
