var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
import { globalObject } from "../pipeline/index.js";
import { emptyVideoCodecs, maybeVideoCodecs, VIDEO_DECODER_CODECS } from "../video.js";
import { getStreamRectCorrected } from "./index.js";
function detectCodecs() {
    if (!("canPlayType" in HTMLVideoElement.prototype)) {
        return maybeVideoCodecs();
    }
    const codecs = emptyVideoCodecs();
    const testElement = document.createElement("video");
    for (const codec in codecs) {
        const supported = testElement.canPlayType(`video/mp4; codecs=${VIDEO_DECODER_CODECS[codec]}`);
        if (supported == "probably") {
            codecs[codec] = true;
        }
        else if (supported == "maybe") {
            codecs[codec] = "maybe";
        }
        else {
            // unsupported
            codecs[codec] = false;
        }
    }
    return codecs;
}
export class VideoElementRenderer {
    static getInfo() {
        return __awaiter(this, void 0, void 0, function* () {
            const supported = "HTMLVideoElement" in globalObject() && "srcObject" in HTMLVideoElement.prototype;
            return {
                environmentSupported: supported,
                supportedVideoCodecs: supported ? detectCodecs() : emptyVideoCodecs()
            };
        });
    }
    constructor() {
        this.implementationName = "video_element";
        this.videoElement = document.createElement("video");
        this.oldTrack = null;
        this.stream = new MediaStream();
        this.size = null;
        this.videoElement.classList.add("video-stream");
        this.videoElement.preload = "none";
        this.videoElement.controls = false;
        this.videoElement.autoplay = true;
        this.videoElement.disablePictureInPicture = true;
        this.videoElement.playsInline = true;
        this.videoElement.muted = true;
        if ("srcObject" in this.videoElement) {
            try {
                this.videoElement.srcObject = this.stream;
            }
            catch (err) {
                if (err.name !== "TypeError") {
                    throw err;
                }
                console.error(err);
                throw `video_element renderer not supported: ${err}`;
            }
        }
    }
    setup(setup) {
        return __awaiter(this, void 0, void 0, function* () {
            this.size = [setup.width, setup.height];
        });
    }
    cleanup() {
        if (this.oldTrack) {
            this.stream.removeTrack(this.oldTrack);
        }
        this.videoElement.srcObject = null;
    }
    setTrack(track) {
        if (this.oldTrack) {
            this.stream.removeTrack(this.oldTrack);
        }
        this.stream.addTrack(track);
        this.oldTrack = track;
    }
    mount(parent) {
        parent.appendChild(this.videoElement);
    }
    unmount(parent) {
        parent.removeChild(this.videoElement);
    }
    onUserInteraction() {
        if (this.videoElement.paused) {
            this.videoElement.play().then(() => {
                // Playing
            }).catch(error => {
                console.error(`Failed to play videoElement: ${error.message || error}`);
            });
        }
    }
    getStreamRect() {
        if (!this.size) {
            return new DOMRect();
        }
        return getStreamRectCorrected(this.videoElement.getBoundingClientRect(), this.size);
    }
    getBase() {
        return null;
    }
}
VideoElementRenderer.type = "videotrack";
