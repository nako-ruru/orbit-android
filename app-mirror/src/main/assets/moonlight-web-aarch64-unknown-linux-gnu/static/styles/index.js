var _a;
import { defaultSettings, getLocalStreamSettings } from "../component/settings_menu.js";
let currentStyle = null;
let styleLink = document.getElementById("style");
export function setStyle(style) {
    if (!currentStyle) {
        document.head.appendChild(styleLink);
    }
    currentStyle = style;
    const file = `styles/${style}.css`;
    if (styleLink.href != file) {
        styleLink.href = file;
    }
}
export function getStyle() {
    // Style is set at the bottom of this page so it cannot be null
    return currentStyle;
}
const settings = getLocalStreamSettings();
const defaultSettings_ = defaultSettings();
setStyle((_a = settings === null || settings === void 0 ? void 0 : settings.pageStyle) !== null && _a !== void 0 ? _a : defaultSettings_.pageStyle);
