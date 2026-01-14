import { addPipePassthrough } from "../pipeline/pipes.js";
export class AudioContextBasePipe {
    constructor(implementationName, base, logger) {
        this.logger = null;
        // TODO: include baseLatency and outputLatency in stats
        this.audioContext = null;
        this.logger = logger !== null && logger !== void 0 ? logger : null;
        this.implementationName = implementationName;
        this.base = base;
    }
    addPipePassthrough() {
        addPipePassthrough(this, ["mount", "unmount"]);
    }
    setup(setup) {
        var _a;
        try {
            this.audioContext = new AudioContext({
                latencyHint: "interactive",
                sampleRate: setup.sampleRate
            });
        }
        catch (e) {
            (_a = this.logger) === null || _a === void 0 ? void 0 : _a.debug(`Failed to setup audio node with latency hint "interactive". Trying to setup without latency hint. ${"toString" in e && typeof e.toString == "function" ? e.toString() : e}`);
        }
        if (!this.audioContext) {
            this.audioContext = new AudioContext({
                sampleRate: setup.sampleRate
            });
        }
        if (this.base && "setup" in this.base && typeof this.base.setup == "function") {
            return this.base.setup(...arguments);
        }
    }
    cleanup() {
        var _a;
        (_a = this.audioContext) === null || _a === void 0 ? void 0 : _a.close();
    }
    onUserInteraction() {
        if (this.base && "onUserInteraction" in this.base && typeof this.base.onUserInteraction == "function") {
            return this.base.onUserInteraction(...arguments);
        }
    }
    getAudioContext() {
        var _a;
        if (!this.audioContext) {
            (_a = this.logger) === null || _a === void 0 ? void 0 : _a.debug("Failed to get audio context", { type: "fatal" });
            throw "Failed to get audio context.";
        }
        return this.audioContext;
    }
    getBase() {
        return this.base;
    }
    // -- Only definition look addPipePassthrough
    mount(_parent) { }
    unmount(_parent) { }
}
