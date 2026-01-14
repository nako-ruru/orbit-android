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
import { allVideoCodecs } from "../video.js";
import { getStreamRectCorrected } from "./index.js";
export class BaseCanvasVideoRenderer {
    constructor(implementationName) {
        this.canvas = document.createElement("canvas");
        this.videoSize = null;
        this.implementationName = implementationName;
        this.canvas.classList.add("video-stream");
    }
    setup(setup) {
        return __awaiter(this, void 0, void 0, function* () {
            this.videoSize = [setup.width, setup.height];
        });
    }
    cleanup() { }
    onUserInteraction() {
        // Nothing
    }
    mount(parent) {
        parent.appendChild(this.canvas);
    }
    unmount(parent) {
        parent.removeChild(this.canvas);
    }
    getStreamRect() {
        if (!this.videoSize) {
            return new DOMRect();
        }
        return getStreamRectCorrected(this.canvas.getBoundingClientRect(), this.videoSize);
    }
    getBase() {
        return null;
    }
}
export class CanvasVideoRenderer extends BaseCanvasVideoRenderer {
    static getInfo() {
        return __awaiter(this, void 0, void 0, function* () {
            // no link
            return {
                environmentSupported: "HTMLCanvasElement" in globalObject() && "CanvasRenderingContext2D" in globalObject(),
                supportedVideoCodecs: allVideoCodecs()
            };
        });
    }
    constructor() {
        super("canvas");
        this.context = null;
        this.animationFrameRequest = null;
        this.currentFrame = null;
    }
    setup(setup) {
        const _super = Object.create(null, {
            setup: { get: () => super.setup }
        });
        return __awaiter(this, void 0, void 0, function* () {
            yield _super.setup.call(this, setup);
            if (this.animationFrameRequest == null) {
                this.animationFrameRequest = requestAnimationFrame(this.onAnimationFrame.bind(this));
            }
        });
    }
    cleanup() {
        super.cleanup();
        this.context = null;
        if (this.animationFrameRequest != null) {
            cancelAnimationFrame(this.animationFrameRequest);
            this.animationFrameRequest = null;
        }
    }
    mount(parent) {
        super.mount(parent);
        if (!this.context) {
            const context = this.canvas.getContext("2d");
            if (context) {
                this.context = context;
            }
            else {
                throw "Failed to get 2d context from canvas";
            }
        }
    }
    submitFrame(frame) {
        var _a;
        (_a = this.currentFrame) === null || _a === void 0 ? void 0 : _a.close();
        this.currentFrame = frame;
    }
    onAnimationFrame() {
        const frame = this.currentFrame;
        if (frame && this.context) {
            this.canvas.width = frame.displayWidth;
            this.canvas.height = frame.displayHeight;
            // Clear the canvas before drawing the new frame to prevent artifacts
            this.context.clearRect(0, 0, this.canvas.width, this.canvas.height);
            this.context.drawImage(frame, 0, 0, this.canvas.width, this.canvas.height);
        }
        this.animationFrameRequest = requestAnimationFrame(this.onAnimationFrame.bind(this));
    }
}
CanvasVideoRenderer.type = "videoframe";
