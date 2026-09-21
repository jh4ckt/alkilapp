const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const INPUT_DIR = __dirname;
const OUTPUT_DIR = path.join(INPUT_DIR, 'play-store');

if (!fs.existsSync(OUTPUT_DIR)) fs.mkdirSync(OUTPUT_DIR);

const files = fs.readdirSync(INPUT_DIR).filter(f => /\.(jpeg|jpg|png)$/i.test(f));

async function process() {
  for (const file of files) {
    const input = path.join(INPUT_DIR, file);
    const base = path.parse(file).name;
    
    // Get metadata
    const meta = await sharp(input).metadata();
    console.log(`${file}: ${meta.width}x${meta.height} (${meta.format})`);

    // 1. Phone: resize to 1080x1920 (maintain aspect, crop if needed)
    await sharp(input)
      .resize(1080, 1920, { fit: 'cover', position: 'centre' })
      .png({ quality: 90 })
      .toFile(path.join(OUTPUT_DIR, `${base}_phone_1080x1920.png`));
    console.log(`  → ${base}_phone_1080x1920.png`);

    // 2. Tablet 7": 1200x1920
    await sharp(input)
      .resize(1200, 1920, { fit: 'cover', position: 'centre' })
      .png({ quality: 90 })
      .toFile(path.join(OUTPUT_DIR, `${base}_tablet7_1200x1920.png`));
    console.log(`  → ${base}_tablet7_1200x1920.png`);

    // 3. Tablet 10": 1920x1200 (landscape)
    await sharp(input)
      .resize(1920, 1200, { fit: 'cover', position: 'centre' })
      .png({ quality: 90 })
      .toFile(path.join(OUTPUT_DIR, `${base}_tablet10_1920x1200.png`));
    console.log(`  → ${base}_tablet10_1920x1200.png`);
  }

  // Special: icon 512x512 (already correct size, just copy if needed)
  const iconPath = path.join(INPUT_DIR, 'ic_launcher_512.png');
  if (fs.existsSync(iconPath)) {
    const meta = await sharp(iconPath).metadata();
    console.log(`ic_launcher_512.png: ${meta.width}x${meta.height}`);
    if (meta.width === 512 && meta.height === 512) {
      fs.copyFileSync(iconPath, path.join(OUTPUT_DIR, 'icon_512x512.png'));
      console.log('  → icon_512x512.png (copied)');
    } else {
      await sharp(iconPath).resize(512, 512).png().toFile(path.join(OUTPUT_DIR, 'icon_512x512.png'));
      console.log('  → icon_512x512.png (resized)');
    }
  }

  console.log('\n✅ Listo en:', OUTPUT_DIR);
}

process().catch(e => console.error(e));