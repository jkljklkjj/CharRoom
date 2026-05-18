const fs = require('fs/promises');
const path = require('path');
const sharp = require('sharp');
const pngToIcoModule = require('png-to-ico');
const pngToIco = pngToIcoModule.default || pngToIcoModule.imagesToIco;

const rootDir = path.resolve(__dirname, '..');
const sourceSvg = path.join(rootDir, 'src/assets/logo.svg');
const publicDir = path.join(rootDir, 'public');

function createSocialPreviewSvg() {
  return `
  <svg width="1200" height="630" viewBox="0 0 1200 630" xmlns="http://www.w3.org/2000/svg">
    <defs>
      <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stop-color="#1f2937" />
        <stop offset="55%" stop-color="#111827" />
        <stop offset="100%" stop-color="#f97316" />
      </linearGradient>
      <radialGradient id="glow" cx="50%" cy="35%" r="60%">
        <stop offset="0%" stop-color="#fb923c" stop-opacity="0.32" />
        <stop offset="100%" stop-color="#fb923c" stop-opacity="0" />
      </radialGradient>
    </defs>
    <rect width="1200" height="630" fill="url(#bg)" />
    <circle cx="980" cy="110" r="260" fill="url(#glow)" />
    <circle cx="170" cy="510" r="220" fill="url(#glow)" />
    <rect x="80" y="78" width="1040" height="474" rx="42" fill="rgba(255,255,255,0.06)" stroke="rgba(255,255,255,0.10)" />
    <text x="130" y="200" fill="#ffffff" font-size="74" font-weight="700" font-family="-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif">轻聊</text>
    <text x="130" y="276" fill="#fbbf24" font-size="32" font-weight="600" font-family="-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif">轻量、智能、跨平台的实时聊天</text>
    <text x="130" y="360" fill="rgba(255,255,255,0.90)" font-size="30" font-weight="400" font-family="-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif">支持高效沟通、群聊协作与智能对话体验</text>
    <text x="130" y="455" fill="rgba(255,255,255,0.70)" font-size="24" font-weight="400" font-family="-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif">www.chatlite.xin</text>
    <circle cx="930" cy="312" r="110" fill="#ffffff" opacity="0.98" />
    <g transform="translate(845 227) scale(0.5)">
      <path fill="#f98701" d="M266.70 336.35 c-13.10 -1.02 -26.67 -3.30 -44.56 -7.46 -10.50 -2.44 -36.77 -9.51 -53.31 -14.33 -28.65 -8.35 -36.51 -10.30 -49.68 -12.34 -21.62 -3.33 -40.50 -2.54 -57.99 2.44 -15.65 4.42 -29.05 12.91 -36.41 23.01 -0.43 0.59 -0.83 1.02 -0.89 0.96 -0.07 -0.03 0.50 -1.85 1.22 -3.99 3.93 -11.32 9.01 -21.72 14.29 -29.15 2.15 -3.04 6.70 -8.55 9.11 -11.06 l1.49 -1.55 -3.80 -3.89 c-11.02 -11.19 -19.47 -23.04 -27.79 -39.05 -6.73 -12.91 -11.32 -25.81 -14.39 -40.40 -5.84 -27.66 -4.46 -56.01 4.13 -83.61 6.40 -20.56 16.73 -39.58 30.99 -56.84 3.60 -4.39 15.35 -16.04 20.33 -20.17 16.50 -13.67 37.86 -25.28 56.58 -30.80 8.12 -2.38 14.46 -3.80 23.60 -5.31 7.92 -1.29 12.11 -1.55 25.42 -1.62 10.43 -0.03 13.57 0.07 17.49 0.56 30.90 3.80 57 14.19 81.03 32.25 20 15.05 36.51 35.09 48.19 58.46 16.14 32.45 21.22 72.85 13.17 104.77 -4.26 16.80 -12.97 32.38 -24.10 43.08 -17.89 17.20 -43.44 26.47 -70.60 25.65 -19.01 -0.59 -37.63 -4.95 -55.39 -12.97 -8.85 -3.99 -14.03 -7.33 -18.75 -12.01 -4.79 -4.79 -7.76 -10.17 -9.11 -16.50 -0.69 -3.40 -0.79 -12.41 -0.13 -17.16 1.62 -12.08 6.40 -25.15 16.40 -44.73 5.35 -10.50 6.90 -13.90 8.55 -18.85 1.52 -4.52 3 -12.05 3.47 -17.46 1.42 -16.27 -4.79 -35.19 -16.21 -49.41 -1.55 -1.98 -2.21 -2.57 -2.87 -2.57 -1.52 0 -8.35 1.68 -12.18 3 -3.99 1.39 -9.44 3.96 -13.63 6.44 -6.54 3.89 -15.12 11.49 -20.27 17.96 -8.95 11.22 -15.84 26.14 -18.85 40.76 -1.72 8.35 -1.98 11.45 -1.95 22.78 0 9.28 0.10 11.16 0.73 15.35 2.71 17.40 9.01 32.18 19.11 44.89 5.22 6.60 13.47 13.93 20.40 18.15 2.87 1.75 9.51 5.05 12.08 5.97 1.09 0.40 1.91 0.76 1.88 0.79 -0.07 0.07 -2.11 -0.03 -4.56 -0.23 -4.92 -0.36 -16.73 -0.17 -22.12 0.40 -14 1.42 -28.62 5.61 -38.29 10.99 -5.51 3.07 -12.44 8.12 -15.51 11.29 l-1.49 1.55 3.89 -2.38 c5.05 -3.04 9.18 -4.95 15.08 -6.93 19.31 -6.50 46.38 -7.29 77.90 -2.28 10.86 1.72 19.94 3.53 40.43 8.05 38.95 8.62 62.91 12.01 94.53 13.40 11.02 0.50 30.27 0.20 36.01 -0.50 2.28 -0.30 5.05 -0.63 6.14 -0.73 l2.01 -0.23 -0.56 3.10 c-1.78 9.34 -5.97 19.87 -10.83 27.20 -8.75 13.17 -20.93 20.17 -39.68 22.78 -4.59 0.66 -14.59 0.89 -19.80 0.50z m-70.87 -97.84 c11.55 -2.21 23.30 -10.83 31.09 -22.74 5.35 -8.22 9.01 -17.92 11.35 -30.07 1.55 -7.92 1.85 -11.45 1.85 -21.16 -0.03 -14.42 -1.75 -24.62 -6.17 -36.47 -10.50 -28.16 -31.95 -48.26 -56.94 -53.34 -4.69 -0.92 -10.99 -1.45 -10.99 -0.86 0 0.17 0.96 1.75 2.11 3.50 4.19 6.24 8.88 16.90 10.93 24.85 3.99 15.35 3 31.39 -2.90 46.47 -0.63 1.65 -3.07 6.90 -5.45 11.72 -6.04 12.38 -8.78 19.41 -10.79 27.86 -2.38 9.77 -2.54 20.50 -0.43 28.25 1.42 5.22 5.08 11.26 9.04 14.79 3.60 3.27 9.51 6.21 14.33 7.16 2.94 0.59 9.94 0.59 12.97 0.03z"/>
    </g>
  </svg>`;
}

async function renderPng(size, fileName) {
  const outputPath = path.join(publicDir, fileName);
  const pngBuffer = await sharp(sourceSvg, { density: 512 })
    .resize(size, size, {
      fit: 'contain',
      background: { r: 255, g: 255, b: 255, alpha: 0 },
    })
    .png()
    .toBuffer();

  await fs.writeFile(outputPath, pngBuffer);
}

async function main() {
  await fs.mkdir(publicDir, { recursive: true });

  await renderPng(16, 'favicon-16x16.png');
  await renderPng(32, 'favicon-32x32.png');
  await renderPng(180, 'apple-touch-icon.png');

  const socialPreviewBuffer = await sharp(Buffer.from(createSocialPreviewSvg()))
    .png()
    .toBuffer();
  await fs.writeFile(path.join(publicDir, 'og-image.png'), socialPreviewBuffer);

  const icoBuffer = await pngToIco([
    path.join(publicDir, 'favicon-16x16.png'),
    path.join(publicDir, 'favicon-32x32.png'),
  ]);

  await fs.writeFile(path.join(publicDir, 'favicon.ico'), icoBuffer);
  console.log('Generated favicon.ico, favicon-16x16.png, favicon-32x32.png, apple-touch-icon.png');
}

main().catch((error) => {
  console.error('Failed to generate favicons');
  console.error(error);
  process.exit(1);
});