/**
 * Seed the Play deck for AuraSpend on the Quiet Ledger tokens.
 *
 * The frames are the concept's own device (see constants.ts QL_DEVICE), the
 * stage is flat paper/ink with no accent blobs, and the type is the font file
 * the app ships. Canvas is 1080x1920 (9:16, the one size Play requires for a
 * phone deck), so every rect below is in that canvas's pixels.
 *
 * Screens come from public/screenshots/android/phone/en/, captured on the
 * emulator at the concept's own geometry (1170x2532 @480dpi = 390x844 dp).
 */
const fs = require("fs");
const path = require("path");

const OUT = path.resolve(__dirname, "app-store-screenshots.json");
const SHOT = (name) => `/screenshots/android/phone/en/${name}.png`;

// The iPhone mockup (public/mockup.png) is 1022x2082; height = width * 2.03718.
const H = (w) => Math.round(w / (1022 / 2082));
const slides = [
  {
    id: "ql-01-hero",
    layout: "hero",
    label: { en: "SMS expense tracker" },
    headline: { en: "Track expenses\nautomatically." },
    screenshot: SHOT("home-light"),
    transforms: {
      caption: { x: 90, y: 128, width: 900, height: 300 },
      device: { x: 210, y: 546, width: 660, height: H(660), zIndex: 3 },
    },
  },
  {
    id: "ql-02-activity",
    layout: "device-bottom",
    inverted: true,
    label: { en: "Automatic" },
    headline: { en: "Every payment,\ncategorised." },
    screenshot: SHOT("activity-dark"),
    transforms: {
      caption: { x: 90, y: 150, width: 900, height: 400 },
      device: { x: 240, y: 638, width: 600, height: H(600), zIndex: 3 },
    },
  },
  {
    id: "ql-03-plan",
    layout: "device-top",
    label: { en: "Budgets" },
    headline: { en: "A budget for\nevery category." },
    screenshot: SHOT("plan-light"),
    transforms: {
      caption: { x: 90, y: 1410, width: 900, height: 330 },
      device: { x: 210, y: -20, width: 660, height: H(660), zIndex: 3 },
    },
  },
  {
    id: "ql-04-insights",
    layout: "device-bottom",
    inverted: true,
    label: { en: "Insights" },
    headline: { en: "See where\nit all goes." },
    screenshot: SHOT("insights-dark"),
    transforms: {
      caption: { x: 90, y: 140, width: 900, height: 300 },
      device: { x: 230, y: 602, width: 620, height: H(620), zIndex: 3 },
    },
  },
  {
    id: "ql-05-quickadd",
    layout: "two-devices",
    label: { en: "Add manually" },
    headline: { en: "Add an expense\nin seconds." },
    screenshot: SHOT("quick-add-light"),
    screenshotSecondary: SHOT("home-light"),
    transforms: {
      caption: { x: 90, y: 120, width: 900, height: 300 },
      deviceSecondary: { x: -10, y: 968, width: 440, height: H(440), zIndex: 2 },
      device: { x: 470, y: 520, width: 660, height: H(660), zIndex: 3 },
    },
  },
  {
    id: "ql-06-subscriptions",
    layout: "device-top",
    inverted: true,
    label: { en: "Subscriptions" },
    headline: { en: "Every recurring\npayment,\ncounted." },
    screenshot: SHOT("subscriptions-dark"),
    transforms: {
      caption: { x: 90, y: 1400, width: 900, height: 440 },
      device: { x: 210, y: -20, width: 660, height: H(660), zIndex: 3 },
    },
  },
  {
    id: "ql-07-trust",
    layout: "no-device",
    inverted: true,
    label: { en: "Open source · No subscriptions" },
    headline: { en: "Offline.\nEncrypted.\nNo ads." },
    screenshot: "",
    transforms: {
      caption: { x: 90, y: 500, width: 900, height: 620 },
    },
    // Five lines, one promise each. The first two carry the privacy claim with
    // its exception on the same line (see tools/check_privacy_claims.sh).
    textElements: [
      {
        id: "trust-1",
        text: { en: "—  Bank SMS is read on-device." },
        transform: { x: 90, y: 1120, width: 900, height: 70 },
        fontSize: 34,
        color: "#9C978A",
        align: "center",
      },
      {
        id: "trust-2",
        text: { en: "—  Nothing is uploaded unless you start a backup." },
        transform: { x: 90, y: 1222, width: 900, height: 70 },
        fontSize: 34,
        color: "#9C978A",
        align: "center",
      },
      {
        id: "trust-3",
        text: { en: "—  No ads. No trackers. No accounts." },
        transform: { x: 90, y: 1324, width: 900, height: 70 },
        fontSize: 34,
        color: "#9C978A",
        align: "center",
      },
      {
        id: "trust-4",
        text: { en: "—  Database encrypted at rest" },
        transform: { x: 90, y: 1426, width: 900, height: 70 },
        fontSize: 34,
        color: "#9C978A",
        align: "center",
      },
      {
        id: "trust-5",
        text: { en: "—  Free and open source, AGPL-3.0" },
        transform: { x: 90, y: 1528, width: 900, height: 70 },
        fontSize: 34,
        color: "#9C978A",
        align: "center",
      },
    ],
  },
];

const state = {
  schemaVersion: 2,
  appName: "AuraSpend",
  themeId: "quiet-ledger",
  // Isolated screens: every exported PNG must stand on its own in the Play
  // carousel, so nothing is allowed to bleed across a seam.
  connectedCanvas: false,
  locales: ["en"],
  locale: "en",
  device: "android",
  orientation: "portrait",
  appIcon: "/app-icon.png",
  slidesByDevice: {
    iphone: [],
    ipad: [],
    android: slides,
    "android-7": [],
    "android-10": [],
    "feature-graphic": [],
  },
};

fs.writeFileSync(OUT, JSON.stringify(state, null, 2) + "\n");
console.log(`wrote ${OUT} — ${slides.length} slides`);
