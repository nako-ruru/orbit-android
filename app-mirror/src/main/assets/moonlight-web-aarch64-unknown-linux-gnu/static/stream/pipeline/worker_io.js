var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
import { globalObject } from "./index.js";
import { addPipePassthrough } from "./pipes.js";
class WorkerReceiverPipe {
    static getInfo() {
        return __awaiter(this, void 0, void 0, function* () {
            return {
                environmentSupported: true
            };
        });
    }
    constructor(base, logger) {
        this.logger = null;
        this.implementationName = `worker_recv -> ${base.implementationName}`;
        this.logger = logger !== null && logger !== void 0 ? logger : null;
        this.base = base;
        addPipePassthrough(this, ["setup", "cleanup", "submitFrame", "submitPacket", "setTrack"]);
    }
    onWorkerMessage(message) {
        if ("call" in message && message.call == "cleanup") {
            this.cleanup();
        }
        else if ("videoSetup" in message) {
            this.setup(message.videoSetup);
        }
        else if ("videoFrame" in message) {
            this.submitFrame(message.videoFrame);
        }
        else if ("data" in message) {
            this.submitPacket(message.data);
        }
        else if ("track" in message) {
            this.setTrack(message.track);
        }
    }
    getBase() {
        return this.base;
    }
    // -- Only definition look addPipePassthrough
    setup(_setup) { }
    cleanup() { }
    submitFrame(_frame) { }
    submitPacket(_buffer) { }
    setTrack(_track) { }
}
WorkerReceiverPipe.type = "workeroutput";
export class WorkerVideoFrameReceivePipe extends WorkerReceiverPipe {
}
WorkerVideoFrameReceivePipe.baseType = "videoframe";
export class WorkerDataReceivePipe extends WorkerReceiverPipe {
}
WorkerDataReceivePipe.baseType = "data";
export class WorkerVideoTrackReceivePipe extends WorkerReceiverPipe {
}
WorkerVideoTrackReceivePipe.baseType = "videotrack";
class WorkerSenderPipe {
    static getInfo() {
        return __awaiter(this, void 0, void 0, function* () {
            return {
                environmentSupported: true
            };
        });
    }
    constructor(base, logger) {
        this.logger = null;
        this.implementationName = `worker_send -> ${base.implementationName}`;
        this.logger = logger !== null && logger !== void 0 ? logger : null;
        this.base = base;
        addPipePassthrough(this);
    }
    getBase() {
        return this.base;
    }
    setup(setup) {
        this.getBase().onWorkerMessage({ videoSetup: setup });
    }
    submitFrame(videoFrame) {
        this.getBase().onWorkerMessage({ videoFrame }, [videoFrame]);
    }
    submitPacket(data) {
        // we don't know if we own this data, so we cannot transfer
        this.getBase().onWorkerMessage({ data });
    }
    setTrack(track) {
        this.getBase().onWorkerMessage({ track }, [track]);
    }
}
WorkerSenderPipe.baseType = "workerinput";
export class WorkerVideoFrameSendPipe extends WorkerSenderPipe {
}
WorkerVideoFrameSendPipe.type = "videoframe";
export class WorkerDataSendPipe extends WorkerSenderPipe {
}
WorkerDataSendPipe.type = "data";
export class WorkerVideoTrackSendPipe extends WorkerSenderPipe {
}
WorkerVideoTrackSendPipe.type = "videotrack";
export class WorkerOffscreenCanvasSendPipe extends WorkerSenderPipe {
    static getInfo() {
        return __awaiter(this, void 0, void 0, function* () {
            return {
                environmentSupported: "OffscreenCanvasRenderingContext2D" in globalObject()
            };
        });
    }
    constructor(base, logger) {
        super(base, logger);
        this.implementationName = "offscreen_canvas_send";
        this.canvas = null;
        this.context = null;
        addPipePassthrough(this);
    }
    setContext(canvas) {
        var _a;
        // This is called from the WorkerPipe
        this.canvas = canvas;
        this.context = canvas.getContext("2d");
        if (!this.context) {
            (_a = this.logger) === null || _a === void 0 ? void 0 : _a.debug("Failed to get OffscreenCanvasContext2D", { type: "fatal" });
        }
    }
    submitFrame(frame) {
        if (this.canvas && this.context) {
            this.canvas.width = frame.displayWidth;
            this.canvas.height = frame.displayHeight;
            this.context.clearRect(0, 0, frame.displayWidth, frame.displayHeight);
            this.context.drawImage(frame, 0, 0, frame.displayWidth, frame.displayHeight);
            if ("commit" in this.canvas && typeof this.canvas.commit == "function") {
                // Signal finished, not supported in all browsers
                this.canvas.commit();
            }
        }
        frame.close();
    }
}
WorkerOffscreenCanvasSendPipe.baseType = "workerinput";
WorkerOffscreenCanvasSendPipe.type = "videoframe";
