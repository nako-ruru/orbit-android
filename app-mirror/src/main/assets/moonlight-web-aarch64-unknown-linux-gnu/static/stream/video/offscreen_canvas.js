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
import { BaseCanvasVideoRenderer } from "./canvas.js";
export class OffscreenCanvasVideoRenderer extends BaseCanvasVideoRenderer {
    static getInfo() {
        return __awaiter(this, void 0, void 0, function* () {
            return {
                environmentSupported: "HTMLCanvasElement" in globalObject() && "transferControlToOffscreen" in HTMLCanvasElement.prototype
            };
        });
    }
    constructor() {
        super("offscreen_canvas");
        this.transferred = false;
        this.offscreen = null;
    }
    setup(setup) {
        const _super = Object.create(null, {
            setup: { get: () => super.setup }
        });
        return __awaiter(this, void 0, void 0, function* () {
            yield _super.setup.call(this, setup);
        });
    }
    mount(parent) {
        super.mount(parent);
        if (!this.offscreen && !this.transferred) {
            this.offscreen = this.canvas.transferControlToOffscreen();
            // The transfer happens in the WorkerPipe
        }
    }
    onWorkerMessage(message) {
        if ("videoSetup" in message) {
            this.setup(message.videoSetup);
        }
    }
}
OffscreenCanvasVideoRenderer.type = "workeroutput";
