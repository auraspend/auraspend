"use client";
import * as React from "react";
import { CAPTURE_SHIFT_PCT, IPHONE_CHROME, PHONE_SCREEN } from "@/lib/constants";
import { img } from "@/lib/image-cache";

type FrameProps = {
  src: string;
  alt?: string;
  style?: React.CSSProperties;
  /** When true, hide EmptySlot placeholder (so it doesn't bake into exports). */
  hideEmpty?: boolean;
};

// iPhone — uses pre-measured mockup.png overlay
export function Phone({ src, alt = "", style, hideEmpty }: FrameProps) {
  const resolved = img(src);
  return (
    <div style={{ position: "relative", aspectRatio: "1022 / 2082", ...style }}>
      <img
        src={img("/mockup.png")}
        alt=""
        style={{ display: "block", width: "100%", height: "100%" }}
        draggable={false}
      />
      <div
        style={{
          position: "absolute",
          zIndex: 10,
          overflow: "hidden",
          left: `${PHONE_SCREEN.L}%`,
          top: `${PHONE_SCREEN.T}%`,
          width: `${PHONE_SCREEN.W}%`,
          height: `${PHONE_SCREEN.H}%`,
          borderRadius: `${PHONE_SCREEN.RX}% / ${PHONE_SCREEN.RY}%`,
          background: "#111",
        }}
      >
        {resolved ? (
          <img
            src={resolved}
            alt={alt}
            style={{ display: "block", width: "100%", height: "100%", objectFit: "cover", objectPosition: "top" }}
            draggable={false}
          />
        ) : hideEmpty ? null : (
          <EmptySlot />
        )}
      </div>
    </div>
  );
}

/**
 * Android Phone slot — an iPhone front, because that is what this deck shows.
 *
 * The bezel is the skill template's `mockup.png` (1022x2082) with the screen
 * rect it was measured for (`PHONE_SCREEN`): 918x1990 at 52,46. The captures are
 * 1170x2532 = 390x844 @3x, ratio 0.4621 against the rect's 0.4613, so they drop
 * in with under a pixel of cover-crop.
 *
 * Inside the screen the Android chrome is cropped away — the status bar behind
 * the drawn one, and the gesture pill off the bottom — and the concept's own
 * iPhone front is drawn in its place: the 46px status bar (9:41, signal, wifi,
 * battery) and the 104x26 island. The status bar is one SVG in a 390x46
 * viewBox, so it scales with the frame instead of needing per-size math.
 */
export function AndroidPhone({ src, alt = "", style, hideEmpty }: FrameProps) {
  const resolved = img(src);
  const bar = IPHONE_CHROME;
  // Right-aligned glyph row: cellular 17, wifi 16, battery 24, gaps of 5.
  const glyphX = bar.SCREEN_W - bar.PAD_X - (17 + bar.GLYPH_GAP + 16 + bar.GLYPH_GAP + 24);
  const glyphY = (bar.STATUS_BAR - bar.GLYPH_H) / 2;
  const timeY = bar.STATUS_BAR / 2 + bar.TIME_SIZE * 0.36;
  return (
    <div style={{ position: "relative", aspectRatio: "1022 / 2082", ...style }}>
      <img
        src={img("/mockup.png")}
        alt=""
        style={{ display: "block", width: "100%", height: "100%" }}
        draggable={false}
      />
      <div
        style={{
          position: "absolute",
          zIndex: 10,
          overflow: "hidden",
          left: `${PHONE_SCREEN.L}%`,
          top: `${PHONE_SCREEN.T}%`,
          width: `${PHONE_SCREEN.W}%`,
          height: `${PHONE_SCREEN.H}%`,
          borderRadius: `${PHONE_SCREEN.RX}% / ${PHONE_SCREEN.RY}%`,
          // Matches the app's own background, so the band above the shifted
          // capture reads as the device, not as a gap.
          background: "var(--ql-paper, #111)",
        }}
      >
        {resolved ? (
          <img
            src={resolved}
            alt={alt}
            style={{
              position: "absolute",
              left: 0,
              top: `${CAPTURE_SHIFT_PCT}%`,
              width: "100%",
              height: "auto",
              display: "block",
            }}
            draggable={false}
          />
        ) : hideEmpty ? null : (
          <EmptySlot />
        )}

        {/* The concept's status bar — .statusbar, 46px, padding 0 30px.
            The band is opaque and painted in the app's own background colour,
            which is what hides the capture's Android status bar: the capture is
            nudged down by (46 - 24)px, so the Android row lands exactly behind
            this band instead of being visible above it. */}
        <div
          aria-hidden
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            width: "100%",
            height: `${(bar.STATUS_BAR / bar.SCREEN_H) * 100}%`,
            background: "var(--ql-paper, #F7F5F0)",
            zIndex: 20,
          }}
        />
        <svg
          viewBox={`0 0 ${bar.SCREEN_W} ${bar.STATUS_BAR}`}
          width="100%"
          aria-hidden
          style={{ display: "block", position: "absolute", top: 0, left: 0, zIndex: 21, color: "inherit" }}
        >
          <text
            x={bar.PAD_X}
            y={timeY}
            fontSize={bar.TIME_SIZE}
            fontWeight={700}
            letterSpacing="0.13"
            fill="currentColor"
            fontFamily="inherit"
          >
            9:41
          </text>
          <g transform={`translate(${glyphX}, ${glyphY})`} fill="currentColor">
            <path d="M0 7h3v4H0zM4.5 5h3v6h-3zM9 2.6h3V11H9zM13.5 0h3v11h-3z" />
            <g transform="translate(22, 0)">
              <path d="M8 10.4 5.6 8a3.4 3.4 0 0 1 4.8 0zM8 6.2a6.6 6.6 0 0 0-4.6 1.9L1.9 6.6a9.4 9.4 0 0 1 12.2 0l-1.5 1.5A6.6 6.6 0 0 0 8 6.2z" />
            </g>
            <g transform="translate(43, 0)">
              <rect x="0.6" y="0.6" width="19" height="9.8" rx="3" fill="none" stroke="currentColor" strokeOpacity="0.45" />
              <rect x="2.4" y="2.4" width="14.4" height="6.2" rx="1.7" />
              <path d="M21.6 4v3" fill="none" stroke="currentColor" strokeOpacity="0.45" strokeWidth="1.4" strokeLinecap="round" />
            </g>
          </g>
        </svg>

        {/* The concept's island — 104x26 at 11px, and it covers the mockup's
            own punch-hole camera dot, which would otherwise peek out below it. */}
        <div
          aria-hidden
          style={{
            position: "absolute",
            top: `${(bar.ISLAND_TOP / bar.SCREEN_H) * 100}%`,
            left: "50%",
            transform: "translateX(-50%)",
            width: `${(bar.ISLAND_W / bar.SCREEN_W) * 100}%`,
            aspectRatio: `${bar.ISLAND_W} / ${bar.ISLAND_H}`,
            borderRadius: "999px",
            background: "#000",
            zIndex: 30,
          }}
        />
      </div>
    </div>
  );
}

export function AndroidTabletP({ src, alt = "", style, hideEmpty }: FrameProps) {
  const resolved = img(src);
  return (
    <div style={{ position: "relative", aspectRatio: "5 / 8", ...style }}>
      <div
        style={{
          width: "100%",
          height: "100%",
          borderRadius: "4.5% / 2.8%",
          background: "linear-gradient(160deg, #2a2a2e 0%, #18181b 100%)",
          boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.08), 0 8px 48px rgba(0,0,0,0.6)",
          position: "relative",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            position: "absolute",
            top: "1.2%",
            left: "50%",
            transform: "translateX(-50%)",
            width: "1.4%",
            height: "0.88%",
            borderRadius: "50%",
            background: "#0d0d0f",
            zIndex: 20,
          }}
        />
        <div
          style={{
            position: "absolute",
            left: "3.5%",
            top: "2.2%",
            width: "93%",
            height: "95.6%",
            borderRadius: "2.5% / 1.6%",
            overflow: "hidden",
            background: "#000",
          }}
        >
          {resolved ? (
            <img
              src={resolved}
              alt={alt}
              style={{ display: "block", width: "100%", height: "100%", objectFit: "cover", objectPosition: "top" }}
              draggable={false}
            />
          ) : hideEmpty ? null : (
            <EmptySlot />
          )}
        </div>
      </div>
    </div>
  );
}

export function AndroidTabletL({ src, alt = "", style, hideEmpty }: FrameProps) {
  const resolved = img(src);
  return (
    <div style={{ position: "relative", aspectRatio: "8 / 5", ...style }}>
      <div
        style={{
          width: "100%",
          height: "100%",
          borderRadius: "2.8% / 4.5%",
          background: "linear-gradient(160deg, #2a2a2e 0%, #18181b 100%)",
          boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.08), 0 8px 48px rgba(0,0,0,0.6)",
          position: "relative",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            position: "absolute",
            left: "1.2%",
            top: "50%",
            transform: "translateY(-50%)",
            width: "0.88%",
            height: "1.4%",
            borderRadius: "50%",
            background: "#0d0d0f",
            zIndex: 20,
          }}
        />
        <div
          style={{
            position: "absolute",
            left: "2.2%",
            top: "3.5%",
            width: "95.6%",
            height: "93%",
            borderRadius: "1.6% / 2.5%",
            overflow: "hidden",
            background: "#000",
          }}
        >
          {resolved ? (
            <img
              src={resolved}
              alt={alt}
              style={{ display: "block", width: "100%", height: "100%", objectFit: "cover", objectPosition: "top" }}
              draggable={false}
            />
          ) : hideEmpty ? null : (
            <EmptySlot />
          )}
        </div>
      </div>
    </div>
  );
}

export function IPad({ src, alt = "", style, hideEmpty }: FrameProps) {
  const resolved = img(src);
  return (
    <div style={{ position: "relative", aspectRatio: "770 / 1000", ...style }}>
      <div
        style={{
          width: "100%",
          height: "100%",
          borderRadius: "5% / 3.6%",
          background: "linear-gradient(180deg, #2C2C2E 0%, #1C1C1E 100%)",
          position: "relative",
          overflow: "hidden",
          boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.1), 0 8px 40px rgba(0,0,0,0.6)",
        }}
      >
        <div
          style={{
            position: "absolute",
            top: "1.2%",
            left: "50%",
            transform: "translateX(-50%)",
            width: "0.9%",
            height: "0.65%",
            borderRadius: "50%",
            background: "#111113",
            zIndex: 20,
          }}
        />
        <div
          style={{
            position: "absolute",
            left: "4%",
            top: "2.8%",
            width: "92%",
            height: "94.4%",
            borderRadius: "2.2% / 1.6%",
            overflow: "hidden",
            background: "#000",
          }}
        >
          {resolved ? (
            <img
              src={resolved}
              alt={alt}
              style={{ display: "block", width: "100%", height: "100%", objectFit: "cover", objectPosition: "top" }}
              draggable={false}
            />
          ) : hideEmpty ? null : (
            <EmptySlot />
          )}
        </div>
      </div>
    </div>
  );
}

function EmptySlot() {
  return (
    <div
      style={{
        width: "100%",
        height: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        color: "rgba(255,255,255,0.4)",
        fontSize: "min(2vw, 14px)",
        background: "linear-gradient(135deg, #1a1a1a 0%, #0a0a0a 100%)",
        textAlign: "center",
        padding: "4%",
      }}
    >
      Drop a screenshot here
    </div>
  );
}
