/*---------------------------------------------------------------------------------------------
 *  BrightScript Engine (https://github.com/lvcabral/brs-engine)
 *
 *  Copyright (c) 2019-2026 Marcelo Lv Cabral. All Rights Reserved.
 *
 *  Licensed under the MIT License. See LICENSE in the repository root for license information.
 *--------------------------------------------------------------------------------------------*/
const fileButton = document.getElementById("fileButton");
const appInfo = document.getElementById("appInfo");
const libVersion = document.getElementById("libVersion");
const display = document.getElementById("display");
const screenFrame = document.getElementById("screenFrame");
const stats = document.getElementById("stats");
const loading = document.getElementById("loading");
const passwordDialog = document.getElementById("passwordDialog");

// App List
const appIcons = [
    document.getElementById("app01"),
    document.getElementById("app02"),
    document.getElementById("app03"),
    document.getElementById("app04"),
];
const appList = [
    {
        id: "home-01",
        title: "Rect Bounce Example",
        version: "1.0.0",
        path: "apps/Rect-Bounce.zip",
        icon: "images/icons/rect-bounce-icon.png",
    },
    {
        id: "home-02",
        title: "Ball Boing Example",
        version: "1.0.0",
        path: "apps/Ball-Boing.zip",
        icon: "images/icons/ball-boing-icon.png",
    },
    {
        id: "home-03",
        title: "Collisions Example",
        version: "1.0.3",
        path: "apps/Collisions.zip",
        icon: "images/icons/collisions-icon.png",
    },
    {
        id: "home-04",
        title: "Custom Video Player",
        version: "1.0.0",
        path: "apps/custom-video-player.zip",
        icon: "images/icons/custom-video-player.png",
    },
];

// App Configuration
// Pause the engine when the browser window loses focus
// If `false` use `roCECStatus` in BrightScript to control app behavior
const pauseOnBlur = false;
let debugMode = "continue";

// App status objects
let currentApp = { id: "", running: false };
let currentZip = null;
let lastLaunch = null;
let resolveEngineReady;
let rejectEngineReady;
const engineReady = new Promise((resolve, reject) => {
    resolveEngineReady = resolve;
    rejectEngineReady = reject;
});
// Keep a failed startup handled even when no package has been opened yet.
engineReady.catch(() => {});

globalThis.brsMobileGetCurrentApp = () => ({ ...currentApp });
globalThis.brsMobileHasReload = () => Boolean(lastLaunch);

// Start the engine
appInfo.innerHTML = "<br/>";
// Custom device configuration (see /api/index.ts for all fields)
const customDeviceInfo = {
    developerId: "UniqueDeveloperId", // As in Roku devices, segregates Registry data
    locale: "pt_BR", // Localize app strings and some SceneGraph Nodes
    displayMode: ["480p", "720p", "1080p"].includes(localStorage.getItem("brsDisplayMode"))
        ? localStorage.getItem("brsDisplayMode")
        : "720p", // Supported modes: 480p (SD), 720p (HD) and 1080p (FHD)
    captionMode: "On", // Enable captions by default for testing
    captionStyle: [],
    captionLanguage: "pt", // Preferred closed caption language
    audioLanguage: "pt", // Preferred audio track language
    maxFps: 30, // Limited refresh rate to minimize issues with iOS/iPadOS
    appList: appList,
    extensions: new Map(),
    // corsProxy: "http://localhost:8080/",
    // Uncomment line above for local testing
    // Or add the URL of an instance of https://github.com/Rob--W/cors-anywhere
};

// Initialize BrightScript Engine when page loads
globalThis.addEventListener("load", () => {
    main().then(resolveEngineReady).catch((error) => {
        rejectEngineReady(error);
        loading.style.visibility = "hidden";
        display.style.opacity = 1;
        appInfo.textContent = "Falha ao iniciar o emulador. Consulte o Console e reabra o RokuDev.";
        console.error("Falha ao iniciar o emulador:", error);
    });
}, false);

function notifyMobileAppState(eventName, data = currentApp) {
    try {
        globalThis.dispatchEvent(new CustomEvent("brs-app-state", {
            detail: { event: eventName, app: data || currentApp }
        }));
    } catch (_) {}
}

// Main function to initialize the BrightScript engine
async function main() {
    // Set App icons
    for (const [index, icon] of appIcons.entries()) {
        icon.src = appList[index].icon;
        icon.title = appList[index].title;
        icon.alt = appList[index].title;
    }

    // Add SceneGraph extension
    const brsSG = brs.SupportedExtension.SceneGraph;
    // The worker resolves relative paths against lib/brs.worker.js, not index.html.
    customDeviceInfo.extensions.set(brsSG, new URL("./lib/brs-sg.js", document.baseURI).href);

    // Caption customization example, once configured in DeviceInfo cannot be dynamically changed in SceneGraph code:
    customDeviceInfo.captionStyle.push(
        { id: "Text/Effect", style: "uniform" },
        { id: "Background/Opacity", style: "50%" }
    );

    const customKeys = new Map();
    customKeys.set("NumpadMultiply", "info"); // Keep consistency with older versions
    customKeys.set("ShiftLeft", "playonly"); // Support for Prince of Persia
    customKeys.set("Shift+ArrowRight", "right"); // Support for Prince of Persia
    customKeys.set("Shift+ArrowLeft", "left"); // Support for Prince of Persia
    customKeys.set("Shift+ArrowUp", "up"); // Support for Prince of Persia
    customKeys.set("Shift+ArrowDown", "down"); // Support for Prince of Persia

    // Initialize device and subscribe to events
    libVersion.innerHTML = brs.getVersion();
    brs.subscribe("web-app", (event, data) => {
        if (event === "loaded") {
            currentApp = data;
            fileButton.style.visibility = "hidden";
            const infoParts = [data.title, data.subtitle].filter(value => String(value || "").trim() !== "");
            if (String(data.version || "").trim() !== "") infoParts.push("v" + data.version);
            appInfo.textContent = infoParts.join(" · ");
            appIconsVisibility("hidden");
            loading.style.visibility = "hidden";
            displayRedraw();
            notifyMobileAppState("loaded", data);
        } else if (event === "started") {
            currentApp = data;
            stats.style.visibility = "visible";
            notifyMobileAppState("started", data);
        } else if (event === "launch") {
            if (data?.app) {
                brs.terminate("EXIT_USER_NAV");
                currentApp = { id: "", running: false };
                currentZip = null;
                loadZip(data.app, data.params);
            }
        } else if (event === "browser") {
            if (data?.url) {
                openBrowser(data.url, data.width, data.height);
            }
        } else if (event === "closed" || event === "error") {
            notifyMobileAppState(event, data);
            closeApp();
        } else if (event === "debug") {
            if (["stop", "pause", "continue"].includes(data.level)) {
                debugMode = data.level;
            }
        } else if (event === "version") {
            console.info(`%cBrightScript Interpreter Library v${data}`, "color: #4A90E2");
            mountZip("./apps/data.zip");
        }
    });
    // initialize() may return before its own asynchronous common.zip download.
    // Supplying the bundled archive makes fonts/certificates available on first launch.
    const assetsResponse = await fetch("./assets/common.zip");
    if (!assetsResponse.ok) throw new Error("Recursos internos indisponíveis: HTTP " + assetsResponse.status);
    customDeviceInfo.assets = await assetsResponse.arrayBuffer();
    await brs.initialize(customDeviceInfo, {
        debugToConsole: true,
        customKeys: customKeys,
        showStats: false,
    });
}
// File selector
const fileSelector = document.getElementById("file");
fileButton.onclick = function () {
    fileSelector.click();
};
fileSelector.onclick = function () {
    this.value = null;
};
fileSelector.onchange = function () {
    const file = this.files?.[0];
    if (!file) return;
    const fileExt = file.name.split(".").pop()?.toLowerCase();
    if (fileExt === "zip" || fileExt === "bpk" || fileExt === "brs") {
        if (fileExt === "bpk") {
            passwordDialog.showModal();
        } else {
            runFile(file);
        }
    } else {
        console.error(`File format not supported: ${fileExt}`);
    }
};
passwordDialog.addEventListener("close", (e) => {
    if (passwordDialog.returnValue === "ok") {
        runFile(fileSelector.files[0], document.forms.passwordForm.password.value);
    }
    document.forms.passwordForm.password.value = "";
});

/**
 * Run files from the file system loaded from the purple button
 * @param {File} file - File object to be executed
 * @param {string} password - Password to decrypt the `bpk` file
 */
async function runFile(file, password = "") {
    try {
        await engineReady;
    } catch (_) { return; }
    const fileExt = file?.name.split(".").pop()?.toLowerCase() ?? "";
    if (fileExt === "zip" || fileExt === "bpk" || fileExt === "brs") {
        file.arrayBuffer()
            .then((data) => {
                // file is loaded
                currentZip = data;
                const options = {
                    clearDisplayOnExit: true,
                    muteSound: false,
                    password: password,
                    debugOnCrash: true,
                };
                const params = new Map([["source", "auto-run-dev"]]);
                lastLaunch = { name: file.name, data, options, params };
                brs.execute(file.name, currentZip, options, params);
            })
            .catch((err) => {
                console.error(`Error opening ${file.name}:${err}`);
            });
    }
}

/**
 * Downloads an app package from the server and execute it
 * @param {string} appId the path to the zip file
 */
async function loadZip(appId, params) {
    try {
        await engineReady;
    } catch (_) { return; }
    if (currentApp.running) {
        console.warn("There is an App already running!");
        return;
    }
    const app = appList.find((app) => app.id === appId);
    if (!app) {
        console.error(`App not found: ${appId}!`);
        return;
    }
    console.log(`Selected App: ${app.id} - ${app.title}`);
    display.style.opacity = 0;
    loading.style.visibility = "visible";
    appIconsVisibility("visible");
    fileSelector.value = null;

    fetch(app.path)
        .then(function (response) {
            if (response.status === 200 || response.status === 0) {
                return response.blob().then(function (zipBlob) {
                    return zipBlob.arrayBuffer().then(function (zipData) {
                        currentZip = zipData;
                        if (!params) {
                            params = new Map([["source", "homescreen"]]);
                        }
                        const options = { entryPoint: true, debugOnCrash: false };
                        lastLaunch = { name: app.path, data: zipData, options, params };
                        brs.execute(app.path, zipData, options, params);
                        display.focus();
                    });
                });
            } else {
                loading.style.visibility = "hidden";
                throw new Error(response.statusText);
            }
        })
        .catch((err) => {
            loading.style.visibility = "hidden";
            display.style.opacity = 1;
            appInfo.textContent = "Não foi possível abrir o exemplo. Tente novamente.";
            console.error(`Error attempting to load zip: ${err.message} (${err.name})`);
        });
}

function reloadLastPackage() {
    if (!lastLaunch || typeof brs === "undefined" || !brs) {
        console.warn("Nenhum pacote disponível para recarregar.");
        return false;
    }
    const launch = lastLaunch;
    const executeAgain = () => {
        try {
            currentZip = launch.data;
            display.style.opacity = 0;
            loading.style.visibility = "visible";
            brs.execute(launch.name, launch.data, launch.options, launch.params);
            display.focus();
        } catch (e) {
            console.error("Falha ao recarregar pacote", e);
        }
    };
    if (currentApp.running) {
        try { brs.terminate("EXIT_USER_NAV"); } catch (_) {}
        setTimeout(executeAgain, 350);
    } else {
        executeAgain();
    }
    return true;
}

globalThis.brsMobileReload = reloadLastPackage;

function mountZip(zip) {
    fetch(zip)
        .then(function (response) {
            if (response.status === 200 || response.status === 0) {
                return response.blob().then(function (zipBlob) {
                    return zipBlob.arrayBuffer().then(function (zipData) {
                        brs.mountExt(zipData);
                    });
                });
            } else {
                throw new Error(response.statusText);
            }
        })
        .catch((err) => {
            console.error(`Error attempting to mount zip: ${err.message} (${err.name})`);
        });
}

// Clear App data and display
function closeApp() {
    currentApp = { id: "", running: false };
    currentZip = null;
    display.style.opacity = 0;
    appInfo.innerHTML = "<br/>";
    fileButton.style.visibility = "visible";
    loading.style.visibility = "hidden";
    stats.style.visibility = "hidden";
    appIconsVisibility("visible");
    fileSelector.value = null;
    notifyMobileAppState("closed", currentApp);
    if (document.fullscreenElement) {
        document.exitFullscreen().catch((err) => {
            console.error(`Error attempting to exit fullscreen mode: ${err.message} (${err.name})`);
        });
    }
}

// Display Fullscreen control
display.addEventListener("dblclick", function (event) {
    event.preventDefault();
    if (currentApp.running) {
        if (document.fullscreenElement) {
            document.exitFullscreen().catch((err) => {
                console.error(`Error attempting to exit fullscreen mode: ${err.message} (${err.name})`);
            });
        } else {
            const fullscreenTarget = screenFrame || display;
            fullscreenTarget.requestFullscreen().catch((err) => {
                console.error(`Error attempting to start fullscreen mode: ${err.message} (${err.name})`);
            });
        }
    }
});

document.onfullscreenchange = function () {
    displayRedraw();
};

display.addEventListener("mousedown", function (event) {
    if (event.detail === 2) {
        event.preventDefault();
    }
});

// Screen Redraw
function displayRedraw() {
    if (document.fullscreenElement) {
        brs.redraw(true);
    } else {
        brs.redraw(false, 854, 480);
        display.style.top = "29px";
    }
}

// App icons Visibility
function appIconsVisibility(visibility) {
    for (const icon of appIcons) {
        icon.style.visibility = visibility;
    }
}

function parseVersionString(str) {
    if (typeof str != "string") {
        return {};
    }
    const vArray = str.split(".");
    return {
        major: Number.parseInt(vArray[0]) || 0,
        minor: Number.parseInt(vArray[1]) || 0,
        patch: Number.parseInt(vArray[2]) || 0,
    };
}

function toggleDiv(divId) {
    const objDiv = document.getElementById(divId);
    const butExpNew = document.getElementById("expand-new");
    const butExp = document.getElementById("expand");
    const butCol = document.getElementById("collapse");
    if (objDiv.style.display == "") {
        objDiv.style.display = "none";
        butExp.style.display = "";
        butCol.style.display = "none";
    } else {
        objDiv.style.display = "";
        butExp.style.display = "none";
        butExpNew.style.display = "none";
        butCol.style.display = "";
    }
}

if (pauseOnBlur) {
    window.onfocus = function () {
        if (currentApp.running && debugMode === "pause") {
            brs.debug("cont");
        }
    };

    window.onblur = function () {
        if (currentApp.running && debugMode === "continue") {
            brs.debug("pause");
        }
    };
}

// Browser Event Handler
function openBrowser(url, width, height) {
    if (!url.startsWith("file://")) {
        try {
            if (globalThis.Android?.openExternalUrl) {
                Android.openExternalUrl(url);
                return;
            }
        } catch (_) {}
        openPopup(url, width, height);
        return;
    }
    if (!url.startsWith("file:///pkg_")) {
        console.error("Error: Invalid local file path!", url);
        return;
    }
    if (!currentZip) {
        console.error("Error: No zip file loaded!", url);
        return;
    }
    if (typeof BrowserFS === "undefined") {
        console.error("BrowserFS is not bundled in RokuDev; local file:// browser windows are unavailable in this build.");
        return;
    }
    const Buffer = BrowserFS.BFSRequire("buffer").Buffer;
    const filePath = url.replace("file:///pkg_", "");

    // Configure BrowserFS
    const fsOptions = {
        fs: "MountableFileSystem",
        options: {
            "pkg:": {
                fs: "ZipFS",
                options: {
                    zipData: Buffer.from(currentZip),
                },
            },
        },
    };
    BrowserFS.configure(fsOptions, (err) => {
        if (err) {
            return console.error(err);
        }
        OpenLocalFile(filePath, width, height);
    });
}

// Function called when BrowserFS is ready
function OpenLocalFile(filePath, width, height) {
    const path = BrowserFS.BFSRequire("path");
    const basePath = path.dirname(filePath);
    const fs = BrowserFS.BFSRequire("fs");
    fs.readFile(`pkg:/${filePath}`, "utf8", function (err, data) {
        if (err) {
            return console.error(err);
        }
        replaceRelativePaths(data, fs, basePath)
            .then((updatedData) => {
                // Open a new window to display the content
                const popup = openPopup("about:blank", width, height);
                popup?.document?.write(updatedData);
            })
            .catch((err) => {
                console.error("Error processing images:", err);
            });
    });
}

// Function to replace relative paths with base64 URLs and add crossorigin attribute
function replaceRelativePaths(html, fs, basePath) {
    const imgRegex = /<img[^>]+src="([^">]+)"/g;
    let match;
    const promises = [];

    function processMatch(match, p1) {
        return new Promise((resolve, reject) => {
            // Check if the path is relative
            if (p1.startsWith("http://") || p1.startsWith("https://")) {
                // Add crossorigin attribute to non-local images if it doesn't already exist
                if (!match[0].includes("crossorigin=")) {
                    html = html.replace(match[0], match[0].replace("<img", "<img crossorigin='anonymous'"));
                }
                resolve();
                return;
            }
            const path = BrowserFS.BFSRequire("path");
            const resolvedPath = path.resolve(basePath, p1);
            fs.readFile(`pkg:/${resolvedPath}`, function (err, fileData) {
                if (err) {
                    console.error(err);
                    reject(err);
                    return;
                }
                const bytes = new Uint8Array(fileData);
                const chunkSize = 0x8000;
                let binary = "";
                for (let i = 0; i < bytes.length; i += chunkSize) {
                    binary += String.fromCharCode(...bytes.subarray(i, Math.min(i + chunkSize, bytes.length)));
                }
                const base64Data = btoa(binary);
                let mimeType = "application/octet-stream";
                const extension = path.extname(p1).replace(/^\./, "").toLowerCase();
                const imageTypes = { jpg: "image/jpeg", jpeg: "image/jpeg", png: "image/png", gif: "image/gif", webp: "image/webp", svg: "image/svg+xml", bmp: "image/bmp" };
                if (extension !== "") {
                    mimeType = imageTypes[extension] || `image/${extension}`;
                }
                const dataURL = `data:${mimeType};base64,${base64Data}`;
                html = html.replace(p1, dataURL);
                resolve();
            });
        });
    }

    while ((match = imgRegex.exec(html)) !== null) {
        promises.push(processMatch(match, match[1]));
    }

    return Promise.all(promises).then(() => html);
}

function openPopup(url, width, height) {
    const newWindow = window.open(url, "_blank", `width=${width},height=${height},popup`);
    if (newWindow) {
        newWindow.focus();
    } else {
        console.error("Failed to open popup window!");
    }
    return newWindow;
}
