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
import { addPipePassthrough } from "../pipeline/pipes.js";
import { allVideoCodecs } from "../video.js";
export class CanvasFrameDrawPipe {
    static getInfo() {
        return __awaiter(this, void 0, void 0, function* () {
            // no link
            return {
                environmentSupported: "CanvasRenderingContext2D" in globalObject() || "OffscreenCanvasRenderingContext2D" in globalObject(),
                supportedVideoCodecs: allVideoCodecs()
            };
        });
    }
    constructor(base, _logger, options) {
        var _a;
        this.animationFrameRequest = null;
        this.currentFrame = null;
        this.implementationName = `canvas_frame -> ${base.implementationName}`;
        this.base = base;
        const opts = options;
        this.drawOnSubmit = (_a = opts === null || opts === void 0 ? void 0 : opts.drawOnSubmit) !== null && _a !== void 0 ? _a : true;
        addPipePassthrough(this);
    }
    setup(setup) {
        var arguments_1 = arguments;
        return __awaiter(this, void 0, void 0, function* () {
            if (this.animationFrameRequest == null) {
                this.animationFrameRequest = requestAnimationFrame(this.onAnimationFrame.bind(this));
            }
            if ("setup" in this.base && typeof this.base.setup == "function") {
                return this.base.setup(...arguments_1);
            }
        });
    }
    cleanup() {
        if ("cleanup" in this.base && typeof this.base.cleanup == "function") {
            return this.base.cleanup(...arguments);
        }
    }
    submitFrame(frame) {
        var _a;
        (_a = this.currentFrame) === null || _a === void 0 ? void 0 : _a.close();
        this.currentFrame = frame;
        if (this.drawOnSubmit) {
            this.drawCurrentFrameIfReady();
        }
    }
    /** Draw currentFrame to canvas if context and frame are ready. Only updates size when dimensions change. */
    drawCurrentFrameIfReady() {
        const frame = this.currentFrame;
        const { context, error } = this.base.useCanvasContext("2d");
        if (!frame || error) {
            return;
        }
        const w = frame.displayWidth;
        const h = frame.displayHeight;
        this.base.setCanvasSize(w, h);
        context.clearRect(0, 0, w, h);
        context.drawImage(frame, 0, 0, w, h);
        this.base.commitFrame();
    }
    onAnimationFrame() {
        if (!this.drawOnSubmit) {
            this.drawCurrentFrameIfReady();
        }
        this.animationFrameRequest = requestAnimationFrame(this.onAnimationFrame.bind(this));
    }
    getBase() {
        return this.base;
    }
}
CanvasFrameDrawPipe.baseType = "canvas";
CanvasFrameDrawPipe.type = "videoframe";
