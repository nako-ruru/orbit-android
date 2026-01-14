var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
import { addPipePassthrough } from "../pipeline/pipes.js";
export class DepacketizeAudioPipe {
    static getInfo() {
        return __awaiter(this, void 0, void 0, function* () {
            return {
                environmentSupported: true
            };
        });
    }
    constructor(base) {
        this.implementationName = `depacketize_audio -> ${base.implementationName}`;
        this.base = base;
        addPipePassthrough(this);
    }
    submitPacket(buffer) {
        this.base.decodeAndPlay({
            data: buffer,
            // TODO: use actual timestamps / durations
            timestampMicroseconds: 0,
            durationMicroseconds: 0,
        });
    }
    getBase() {
        return this.base;
    }
}
DepacketizeAudioPipe.baseType = "data";
DepacketizeAudioPipe.type = "data";
