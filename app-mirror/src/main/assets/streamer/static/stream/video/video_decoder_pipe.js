var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
import { ByteBuffer } from "../buffer.js";
import { globalObject } from "../pipeline/index.js";
import { addPipePassthrough } from "../pipeline/pipes.js";
import { emptyVideoCodecs, maybeVideoCodecs } from "../video.js";
export const VIDEO_DECODER_CODECS_IN_BAND = {
    // avc1 = out of band config, avc3 = in band with sps, pps, idr
    "H264": "avc3.42E01E",
    "H264_HIGH8_444": "avc3.640032",
    // hvc1 = out of band config, hev1 = in band with sps, pps, idr
    "H265": "hev1.1.6.L93.B0",
    "H265_MAIN10": "hev1.2.4.L120.90",
    "H265_REXT8_444": "hev1.6.6.L93.90",
    "H265_REXT10_444": "hev1.6.10.L120.90",
    // av1 doesn't have in band and out of band distinction
    "AV1_MAIN8": "av01.0.04M.08",
    "AV1_MAIN10": "av01.0.04M.10",
    "AV1_HIGH8_444": "av01.0.08M.08",
    "AV1_HIGH10_444": "av01.0.08M.10"
};
const VIDEO_DECODER_CODECS_OUT_OF_BAND = {
    "H264": "avc1.42E01E",
    "H264_HIGH8_444": "avc1.640032",
    "H265": "hvc1.1.6.L93.B0",
    "H265_MAIN10": "hvc1.2.4.L120.90",
    "H265_REXT8_444": "hvc1.6.6.L93.90",
    "H265_REXT10_444": "hvc1.6.10.L120.90",
    "AV1_MAIN8": "av01.0.04M.08",
    "AV1_MAIN10": "av01.0.04M.10",
    "AV1_HIGH8_444": "av01.0.08M.08",
    "AV1_HIGH10_444": "av01.0.08M.10"
};
function detectCodecs() {
    return __awaiter(this, void 0, void 0, function* () {
        if (!("isConfigSupported" in VideoDecoder)) {
            return maybeVideoCodecs();
        }
        const codecs = emptyVideoCodecs();
        for (const codec in codecs) {
            // TODO: parallelize await?
            const supportedInBand = yield VideoDecoder.isConfigSupported({
                codec: VIDEO_DECODER_CODECS_IN_BAND[codec]
            });
            const supportedOutOfBand = yield VideoDecoder.isConfigSupported({
                codec: VIDEO_DECODER_CODECS_OUT_OF_BAND[codec]
            });
            codecs[codec] = supportedInBand.supported || supportedOutOfBand.supported ? true : false;
        }
        // TODO: Firefox, Safari say they can play this codec, but they can't
        codecs.H264_HIGH8_444 = false;
        return codecs;
    });
}
function getIfConfigSupported(config) {
    return __awaiter(this, void 0, void 0, function* () {
        const supported = yield VideoDecoder.isConfigSupported(config);
        if (supported.supported) {
            return config;
        }
        return null;
    });
}
export class VideoDecoderPipe {
    static getInfo() {
        return __awaiter(this, void 0, void 0, function* () {
            const supported = "VideoDecoder" in globalObject();
            return {
                environmentSupported: supported,
                supportedVideoCodecs: supported ? yield detectCodecs() : emptyVideoCodecs()
            };
        });
    }
    constructor(base, logger) {
        this.fps = 0;
        this.errored = false;
        this.config = null;
        this.translator = null;
        this.decoderSetupFinished = false;
        this.requestedIdr = false;
        this.needsKeyFrame = true;
        this.bufferedUnits = [];
        this.implementationName = `video_decoder -> ${base.implementationName}`;
        this.logger = logger !== null && logger !== void 0 ? logger : null;
        this.base = base;
        this.decoder = new VideoDecoder({
            error: this.onError.bind(this),
            output: this.onOutput.bind(this)
        });
        addPipePassthrough(this);
    }
    onError(error) {
        var _a;
        this.errored = true;
        (_a = this.logger) === null || _a === void 0 ? void 0 : _a.debug(`VideoDecoder has an error ${"toString" in error ? error.toString() : `${error}`}`, { type: "fatal" });
        console.error(error);
    }
    onOutput(frame) {
        this.base.submitFrame(frame);
    }
    trySetConfig(codec) {
        return __awaiter(this, void 0, void 0, function* () {
            if (!this.config) {
                this.config = yield getIfConfigSupported({
                    codec,
                    hardwareAcceleration: "prefer-hardware",
                    optimizeForLatency: true
                });
            }
            if (!this.config) {
                this.config = yield getIfConfigSupported({
                    codec,
                    optimizeForLatency: true
                });
            }
            if (!this.config) {
                this.config = yield getIfConfigSupported({
                    codec,
                });
            }
        });
    }
    setup(setup) {
        var arguments_1 = arguments;
        return __awaiter(this, void 0, void 0, function* () {
            var _a, _b, _c, _d, _e, _f, _g;
            this.fps = setup.fps;
            const codec = VIDEO_DECODER_CODECS_IN_BAND[setup.codec];
            yield this.trySetConfig(codec);
            if (!this.config) {
                if (setup.codec == "H264" || setup.codec == "H264_HIGH8_444") {
                    this.translator = new H264StreamVideoTranslator((_a = this.logger) !== null && _a !== void 0 ? _a : undefined);
                    const codec = VIDEO_DECODER_CODECS_OUT_OF_BAND[setup.codec];
                    yield this.trySetConfig(codec);
                }
                else if (setup.codec == "H265" || setup.codec == "H265_MAIN10" || setup.codec == "H265_REXT8_444" || setup.codec == "H265_REXT10_444") {
                    this.translator = new H265StreamVideoTranslator((_b = this.logger) !== null && _b !== void 0 ? _b : undefined);
                    const codec = VIDEO_DECODER_CODECS_OUT_OF_BAND[setup.codec];
                    yield this.trySetConfig(codec);
                }
                else if (setup.codec == "AV1_MAIN8" || setup.codec == "AV1_MAIN10" || setup.codec == "AV1_HIGH8_444" || setup.codec == "AV1_HIGH10_444") {
                    this.errored = true;
                    (_c = this.logger) === null || _c === void 0 ? void 0 : _c.debug("Av1 stream translator is not implemented currently!", { type: "fatalDescription" });
                    return;
                }
                else {
                    this.errored = true;
                    (_d = this.logger) === null || _d === void 0 ? void 0 : _d.debug(`Failed to find stream translator for codec ${setup.codec}`);
                    return;
                }
            }
            if (!this.config) {
                this.errored = true;
                (_e = this.logger) === null || _e === void 0 ? void 0 : _e.debug(`Failed to setup VideoDecoder for codec ${setup.codec} because of missing config`);
                return;
            }
            (_f = this.translator) === null || _f === void 0 ? void 0 : _f.setBaseConfig(this.config);
            (_g = this.logger) === null || _g === void 0 ? void 0 : _g.debug(`VideoDecoder config: ${JSON.stringify(this.config)}`);
            this.reset();
            this.decoderSetupFinished = true;
            if ("setup" in this.base && typeof this.base.setup == "function") {
                return yield this.base.setup(...arguments_1);
            }
        });
    }
    submitDecodeUnit(unit) {
        var _a;
        if (this.errored) {
            console.debug("Cannot submit video decode unit because the stream errored");
            return;
        }
        if (!this.decoderSetupFinished) {
            this.bufferedUnits.push(unit);
            return;
        }
        if (this.bufferedUnits.length > 0) {
            const bufferedUnits = this.bufferedUnits.splice(0);
            for (const bufferedUnit of bufferedUnits) {
                this.submitDecodeUnit(bufferedUnit);
            }
        }
        if (this.translator) {
            const value = this.translator.submitDecodeUnit(unit);
            if (value.error) {
                this.errored = true;
                (_a = this.logger) === null || _a === void 0 ? void 0 : _a.debug("VideoDecoder has errored!");
                return;
            }
            const { configure, chunk } = value;
            if (!chunk) {
                console.debug("No chunk received!");
                return;
            }
            if (configure) {
                console.debug("Resetting video decoder config with", configure);
                this.decoder.reset();
                this.decoder.configure(configure);
                // This likely is an idr
                this.requestedIdr = false;
            }
            this.decoder.decode(chunk);
        }
        else {
            if (unit.type != "key" && this.needsKeyFrame) {
                return;
            }
            this.needsKeyFrame = false;
            this.requestedIdr = false;
            const chunk = new EncodedVideoChunk({
                type: unit.type,
                data: unit.data,
                timestamp: unit.timestampMicroseconds,
                duration: unit.durationMicroseconds
            });
            this.decoder.decode(chunk);
        }
    }
    reset() {
        var _a;
        if (!this.translator) {
            this.decoder.reset();
            this.needsKeyFrame = true;
            if (this.config) {
                this.decoder.configure(this.config);
            }
            else {
                (_a = this.logger) === null || _a === void 0 ? void 0 : _a.debug("Failed to configure VideoDecoder because of missing config", { type: "fatal" });
            }
        }
        else if (this.config) {
            this.translator.setBaseConfig(this.config);
        }
    }
    pollRequestIdr() {
        let requestIdr = false;
        const estimatedQueueDelayMs = this.decoder.decodeQueueSize * 1000 / this.fps;
        if (estimatedQueueDelayMs > 200 && this.decoder.decodeQueueSize > 2) {
            // We have more than 200ms second backlog in the decoder
            // -> This decoder is ass, request idr, flush that decoder
            if (!this.requestedIdr) {
                requestIdr = true;
                this.reset();
            }
            console.debug(`Requesting idr because of decode queue size(${this.decoder.decodeQueueSize}) and estimated delay of the queue: ${estimatedQueueDelayMs}`);
        }
        if ("pollRequestIdr" in this.base && typeof this.base.pollRequestIdr == "function") {
            if (this.base.pollRequestIdr(...arguments)) {
                requestIdr = true;
            }
        }
        if (requestIdr) {
            this.requestedIdr = true;
        }
        return requestIdr;
    }
    cleanup() {
        this.decoder.close();
        if ("cleanup" in this.base && typeof this.base.cleanup == "function") {
            return this.base.cleanup(arguments);
        }
    }
    getBase() {
        return this.base;
    }
}
VideoDecoderPipe.baseType = "videoframe";
VideoDecoderPipe.type = "videodata";
const START_CODE_SHORT = new Uint8Array([0x00, 0x00, 0x01]); // 3-byte start code
const START_CODE_LONG = new Uint8Array([0x00, 0x00, 0x00, 0x01]); // 4-byte start code
function startsWith(buffer, position, check) {
    for (let i = 0; i < check.length; i++) {
        if (buffer[position + i] != check[i]) {
            return false;
        }
    }
    return true;
}
class CodecStreamTranslator {
    constructor(logger) {
        this.decoderConfig = null;
        this.currentFrame = new Uint8Array(1000);
        this.logger = logger !== null && logger !== void 0 ? logger : null;
    }
    setBaseConfig(decoderConfig) {
        this.decoderConfig = decoderConfig;
    }
    getCurrentConfig() {
        return this.decoderConfig;
    }
    submitDecodeUnit(unit) {
        var _a;
        if (!this.decoderConfig) {
            (_a = this.logger) === null || _a === void 0 ? void 0 : _a.debug("Failed to retrieve decoderConfig which should already exist for VideoDecoder", { type: "fatal" });
            return { error: true };
        }
        // We're getting annex b prefixed nalus but we need length prefixed nalus -> convert them based on codec
        const { shouldProcess } = this.startProcessChunk(unit);
        if (!shouldProcess) {
            return { configure: null, chunk: null, error: false };
        }
        const data = new Uint8Array(unit.data);
        let unitBegin = 0;
        let currentPosition = 0;
        let currentFrameSize = 0;
        let handleStartCode = () => {
            const slice = data.slice(unitBegin, currentPosition);
            const { include } = this.onChunkUnit(slice);
            if (include) {
                // Append size + data
                this.checkFrameBufferSize(currentFrameSize, slice.length + 4);
                // Append size
                const sizeBuffer = new ByteBuffer(4);
                sizeBuffer.putU32(slice.length);
                sizeBuffer.flip();
                this.currentFrame.set(sizeBuffer.getRemainingBuffer(), currentFrameSize);
                // Append data
                this.currentFrame.set(slice, currentFrameSize + 4);
                currentFrameSize += slice.length + 4;
            }
        };
        while (currentPosition < data.length) {
            let startCodeLength = 0;
            let foundStartCode = false;
            if (startsWith(data, currentPosition, START_CODE_LONG)) {
                foundStartCode = true;
                startCodeLength = START_CODE_LONG.length;
            }
            else if (startsWith(data, currentPosition, START_CODE_SHORT)) {
                foundStartCode = true;
                startCodeLength = START_CODE_SHORT.length;
            }
            if (foundStartCode) {
                if (currentPosition != 0) {
                    handleStartCode();
                }
                currentPosition += startCodeLength;
                unitBegin = currentPosition;
            }
            else {
                currentPosition += 1;
            }
        }
        // The last nal also needs to get processed
        handleStartCode();
        const { reconfigure } = this.endChunk();
        const chunk = new EncodedVideoChunk({
            type: unit.type,
            timestamp: unit.timestampMicroseconds,
            duration: unit.durationMicroseconds,
            data: this.currentFrame.slice(0, currentFrameSize),
        });
        return {
            configure: reconfigure ? this.decoderConfig : null,
            chunk,
            error: false
        };
    }
    checkFrameBufferSize(currentSize, requiredExtra) {
        if (currentSize + requiredExtra > this.currentFrame.length) {
            const newFrame = new Uint8Array((currentSize + requiredExtra) * 2);
            newFrame.set(this.currentFrame);
            this.currentFrame = newFrame;
        }
    }
}
// TODO: search for the spec of Avcc and adjust these to better comply / have more info
function h264NalType(header) {
    return header & 0x1f;
}
function h264MakeAvcC(sps, pps) {
    const size = 7 + // header
        2 + sps.length + // SPS
        1 + // PPS count
        2 + pps.length; // PPS
    const data = new Uint8Array(size);
    let i = 0;
    data[i++] = 0x01; // configurationVersion
    data[i++] = sps[1]; // AVCProfileIndication
    data[i++] = sps[2]; // profile_compatibility
    data[i++] = sps[3]; // AVCLevelIndication
    data[i++] = 0xFF; // lengthSizeMinusOne = 3 (4 bytes)
    data[i++] = 0xE1; // numOfSPS = 1
    data[i++] = sps.length >> 8;
    data[i++] = sps.length & 0xff;
    data.set(sps, i);
    i += sps.length;
    data[i++] = 0x01; // numOfPPS = 1
    data[i++] = pps.length >> 8;
    data[i++] = pps.length & 0xff;
    data.set(pps, i);
    return data;
}
class H264StreamVideoTranslator extends CodecStreamTranslator {
    constructor(logger) {
        super(logger);
        this.hasDescription = false;
        this.pps = null;
        this.sps = null;
    }
    startProcessChunk(unit) {
        return {
            shouldProcess: unit.type == "key" || this.hasDescription
        };
    }
    onChunkUnit(slice) {
        const nalType = h264NalType(slice[0]);
        if (nalType == 7) {
            // Sps
            this.sps = new Uint8Array(slice);
            return { include: false };
        }
        else if (nalType == 8) {
            // Pps
            this.pps = new Uint8Array(slice);
            return { include: false };
        }
        return { include: true };
    }
    endChunk() {
        var _a;
        if (!this.decoderConfig) {
            throw "UNREACHABLE";
        }
        if (this.pps && this.sps) {
            const description = h264MakeAvcC(this.sps, this.pps);
            this.sps = null;
            this.pps = null;
            this.decoderConfig.description = description;
            console.debug("Reset decoder config using Sps and Pps");
            this.hasDescription = true;
            return { reconfigure: true };
        }
        else if (!this.hasDescription) {
            (_a = this.logger) === null || _a === void 0 ? void 0 : _a.debug("Received key frame without Sps and Pps", { type: "fatal" });
        }
        return { reconfigure: false };
    }
}
function h265NalType(header) {
    return (header >> 1) & 0x3f;
}
function h265MakeHvcC(vps, sps, pps) {
    // Minimal hvcC with 3 arrays (VPS/SPS/PPS)
    const size = 23 + // fixed header (minimal compliant)
        (3 * 3) + // array headers
        (2 + vps.length) +
        (2 + sps.length) +
        (2 + pps.length);
    const data = new Uint8Array(size);
    let i = 0;
    data[i++] = 1; // configurationVersion
    // profile_tier_level
    data[i++] = (sps[1] >> 1) & 0x3f; // general_profile_space/tier/profile_idc
    data[i++] = 0; // general_profile_compatibility_flags (part 1)
    data[i++] = 0;
    data[i++] = 0;
    data[i++] = 0;
    data[i++] = 0; // general_constraint_indicator_flags (6 bytes)
    data[i++] = 0;
    data[i++] = 0;
    data[i++] = 0;
    data[i++] = 0;
    data[i++] = 0;
    data[i++] = sps[12]; // general_level_idc (heuristic, works in practice)
    data[i++] = 0xF0; // min_spatial_segmentation_idc
    data[i++] = 0x00;
    data[i++] = 0xFC; // parallelismType
    data[i++] = 0xFD; // chromaFormat
    data[i++] = 0xF8; // bitDepthLumaMinus8
    data[i++] = 0xF8; // bitDepthChromaMinus8
    data[i++] = 0x00; // avgFrameRate (2 bytes)
    data[i++] = 0x00;
    data[i++] = 0x0F; // constantFrameRate + numTemporalLayers + lengthSizeMinusOne
    data[i++] = 3; // numOfArrays
    // VPS
    data[i++] = 0x20; // array_completeness=0, nal_unit_type=32
    data[i++] = 0;
    data[i++] = 1;
    data[i++] = vps.length >> 8;
    data[i++] = vps.length & 0xff;
    data.set(vps, i);
    i += vps.length;
    // SPS
    data[i++] = 0x21; // nal_unit_type=33
    data[i++] = 0;
    data[i++] = 1;
    data[i++] = sps.length >> 8;
    data[i++] = sps.length & 0xff;
    data.set(sps, i);
    i += sps.length;
    // PPS
    data[i++] = 0x22; // nal_unit_type=34
    data[i++] = 0;
    data[i++] = 1;
    data[i++] = pps.length >> 8;
    data[i++] = pps.length & 0xff;
    data.set(pps, i);
    return data;
}
class H265StreamVideoTranslator extends CodecStreamTranslator {
    constructor(logger) {
        super(logger);
        this.hasDescription = false;
        this.vps = null;
        this.sps = null;
        this.pps = null;
    }
    startProcessChunk(unit) {
        return {
            shouldProcess: unit.type === "key" || this.hasDescription
        };
    }
    onChunkUnit(slice) {
        const nalType = h265NalType(slice[0]);
        if (nalType === 32) {
            this.vps = new Uint8Array(slice);
            return { include: false };
        }
        if (nalType === 33) {
            this.sps = new Uint8Array(slice);
            return { include: false };
        }
        if (nalType === 34) {
            this.pps = new Uint8Array(slice);
            return { include: false };
        }
        return { include: true };
    }
    endChunk() {
        var _a;
        if (!this.decoderConfig) {
            throw "UNREACHABLE";
        }
        if (this.vps && this.sps && this.pps) {
            this.decoderConfig.description =
                h265MakeHvcC(this.vps, this.sps, this.pps);
            this.vps = this.sps = this.pps = null;
            this.hasDescription = true;
            console.debug("Reset decoder config using VPS/SPS/PPS");
            return { reconfigure: true };
        }
        if (!this.hasDescription) {
            (_a = this.logger) === null || _a === void 0 ? void 0 : _a.debug("Received key frame without VPS/SPS/PPS");
        }
        return { reconfigure: false };
    }
}
