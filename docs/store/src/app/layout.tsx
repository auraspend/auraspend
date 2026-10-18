import type { Metadata } from "next";
import localFont from "next/font/local";
import "./globals.css";

// The app's own font file (app/src/main/res/font/plus_jakarta_sans.ttf, OFL —
// see the licences index), copied here so the deck's type is the type that
// ships rather than a lookalike webfont. It is a variable font (fvar), so the
// whole 200-800 range is real weight, not synthesised bold.
const font = localFont({
  src: "./fonts/plus-jakarta-sans.ttf",
  weight: "200 800",
  display: "swap",
  variable: "--font-jakarta",
});

export const metadata: Metadata = {
  title: "AuraSpend — Play Store screenshots",
  description: "Design and export Google Play screenshots for AuraSpend.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className={font.variable} style={{ fontFamily: "var(--font-jakarta), system-ui, sans-serif" }}>
        {children}
      </body>
    </html>
  );
}
