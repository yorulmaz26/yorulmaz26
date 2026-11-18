"""tool/icon_1024.png dosyasından Android ve iOS ikon boyutlarını üretir."""
import json, os
from PIL import Image

src = Image.open('tool/icon_1024.png').convert('RGB')  # iOS ikonları alfa içermemeli
android = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
for d, px in android.items():
    src.resize((px, px), Image.LANCZOS).save(f'android/app/src/main/res/mipmap-{d}/ic_launcher.png')

ios_dir = 'ios/Runner/Assets.xcassets/AppIcon.appiconset'
contents = json.load(open(os.path.join(ios_dir, 'Contents.json')))
for img in contents['images']:
    size = float(img['size'].split('x')[0])
    scale = int(img['scale'].replace('x', ''))
    px = int(round(size * scale))
    src.resize((px, px), Image.LANCZOS).save(os.path.join(ios_dir, img['filename']))
print('ikonlar üretildi')
