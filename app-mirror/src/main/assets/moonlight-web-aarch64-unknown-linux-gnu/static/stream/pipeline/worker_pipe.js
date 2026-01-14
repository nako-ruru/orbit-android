var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
import { OffscreenCanvasVideoRenderer } from "../video/offscreen_canvas.js";
import { globalObject, pipelineToString } from "./index.js";
import { addPipePassthrough } from "./pipes.js";
export function createPipelineWorker() {
    if (!("Worker" in globalObject())) {
        return null;
    }
    return new Worker(new URL("worker.js", import.meta.url), { type: "module" });
}
export class WorkerPipe {
    static getInfoInternal(pipeline) {
        return __awaiter(this, void 0, void 0, function* () {
            const worker = createPipelineWorker();
            if (!worker) {
                return {
                    environmentSupported: false
                };
            }
            const sendMessage = { checkSupport: pipeline };
            worker.postMessage(sendMessage);
            const info = yield new Promise((resolve, reject) => {
                worker.onmessage = (event) => {
                    const message = event.data;
                    if ("checkSupport" in message) {
                        resolve(message.checkSupport);
                    }
                    else if ("log" in message) {
                        throw message.log;
                    }
                    else {
                        throw "Failed to get info about worker pipeline because it returned a wrong message";
                    }
                };
                worker.onerror = reject;
            });
            return info;
        });
    }
    constructor(base, pipeline, logger) {
        this.implementationName = `worker_pipe [${pipelineToString(pipeline)}] -> ${base.implementationName}`;
        this.logger = logger !== null && logger !== void 0 ? logger : null;
        // TODO: check that the pipeline starts with output and ends with input
        this.base = base;
        this.pipeline = pipeline;
        const worker = createPipelineWorker();
        if (!worker) {
            throw "Failed to create worker pipeline: Workers not supported!";
        }
        this.worker = worker;
        this.worker.onmessage = this.onReceiveWorkerMessage.bind(this);
        const message = {
            createPipeline: this.pipeline
        };
        this.worker.postMessage(message);
        addPipePassthrough(this);
    }
    onWorkerMessage(input, transfer) {
        const message = { input };
        this.worker.postMessage(message, transfer !== null && transfer !== void 0 ? transfer : []);
    }
    onReceiveWorkerMessage(event) {
        var _a;
        const data = event.data;
        if ("output" in data) {
            this.base.onWorkerMessage(data.output);
        }
        else if ("log" in data) {
            (_a = this.logger) === null || _a === void 0 ? void 0 : _a.debug(data.log, data.info);
        }
    }
    mount() {
        var _a;
        let result;
        if ("mount" in this.base && typeof this.base.mount == "function") {
            result = this.base.mount(...arguments);
        }
        // The OffscreenCanvas needs to transfer it's canvas into the worker, do that here
        if (this.base instanceof OffscreenCanvasVideoRenderer && this.base.offscreen) {
            (_a = this.logger) === null || _a === void 0 ? void 0 : _a.debug("TESTING: TRANSFERRED");
            const canvas = this.base.offscreen;
            this.onWorkerMessage({ canvas }, [canvas]);
            this.base.transferred = true;
            this.base.offscreen = null;
        }
        return result;
    }
    cleanup() {
        this.worker.terminate();
        if ("cleanup" in this.base && typeof this.base.cleanup == "function") {
            return this.base.cleanup(...arguments);
        }
    }
    getBase() {
        return this.base;
    }
}
export function workerPipe(name, pipeline) {
    class CustomWorkerPipe extends WorkerPipe {
        static getInfo() {
            return __awaiter(this, void 0, void 0, function* () {
                return yield this.getInfoInternal(pipeline);
            });
        }
        constructor(base, logger) {
            super(base, pipeline, logger);
        }
    }
    CustomWorkerPipe.baseType = "workeroutput";
    CustomWorkerPipe.type = "workerinput";
    Object.defineProperty(CustomWorkerPipe, "name", { value: name });
    return CustomWorkerPipe;
}
