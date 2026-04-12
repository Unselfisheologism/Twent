# UI Redesign Script - Blue, Vermillion Orange, Grey Palette
# This script updates all hardcoded colors in XML layout files

import re
import os

# Color mapping for common replacements
COLOR_REPLACEMENTS = {
    # Old purples/teals -> New blues
    '#FFBB86FC': '@color/blue_accent',  # purple_200
    '#FF6200EE': '@color/blue_primary',  # purple_500
    '#FF3700B3': '@color/blue_dark',  # purple_700
    '#FF03DAC5': '@color/blue_secondary',  # teal_200
    '#FF018786': '@color/blue_primary',  # teal_700
    '#C882FF': '@color/blue_primary',
    '#A182FF': '@color/blue_secondary',
    '#5880F7': '@color/blue_primary',
    
    # Old neon colors -> New palette
    '#00D4FF': '@color/blue_primary',
    '#B829DD': '@color/vermillion_primary',
    '#FF2D78': '@color/vermillion_primary',
    '#39FF14': '@color/success_green',
    '#FFD300': '@color/gold_badge',
    '#00FF88': '@color/success_green',
    '#FF8C00': '@color/vermillion_primary',
    '#FF3B5C': '@color/error_red',
    '#FF6B35': '@color/vermillion_secondary',
    
    # Grays -> New grey palette
    '#8A8A8E': '@color/text_gray',
    '#CECECE': '@color/text_secondary',
    '#888888': '@color/text_secondary',
    '#666666': '@color/text_secondary',
    '#2E2E2E': '@color/grey_800',
    '#333333': '@color/grey_800',
    '#424242': '@color/grey_700',
    '#616161': '@color/grey_600',
    '#9E9E9E': '@color/grey_500',
    '#BDBDBD': '@color/grey_400',
    '#E0E0E0': '@color/grey_300',
    '#EEEEEE': '@color/grey_200',
    '#F5F5F5': '@color/grey_100',
    '#212121': '@color/grey_900',
    '#303030': '@color/grey_800',
    
    # Backgrounds
    '#101010': '@color/background',
    '#0A0A0F': '@color/dark_bg',
    '#151520': '@color/dark_surface',
    '#1E1E2E': '@color/dark_surface_high',
    '#2A2A3E': '@color/dark_surface_highest',
    '#1A1A2E': '@color/grey_900',
    '#FAFAFA': '@color/light_bg',
    '#F0F0F5': '@color/light_surface_high',
    '#E8E8F0': '@color/light_surface_highest',
    '#1C1C1E': '@color/panel_background',
    '#2E2F33': '@color/input_box_background',
    
    # Specific UI elements
    '#4CAF50': '@color/blue_primary',  # Green accents -> Blue
    '#2196F3': '@color/blue_primary',  # Blue (keep blue)
    '#FF9800': '@color/vermillion_primary',  # Orange -> Vermillion
    '#F44336': '@color/error_red',  # Error red
    '#FFBC87': '@color/vermillion_accent',  # Old orange
    '#00BCD4': '@color/blue_primary',  # Cyan -> Blue
    
    # Misc
    '#3663FF': '@color/blue_primary',  # Old button blue
}

def replace_colors_in_file(file_path):
    """Replace hardcoded colors in a file"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        original_content = content
        
        for old_color, new_color in COLOR_REPLACEMENTS.items():
            # Case-insensitive replacement for hex colors
            pattern = re.compile(re.escape(old_color), re.IGNORECASE)
            content = pattern.sub(new_color, content)
        
        if content != original_content:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"✓ Updated: {file_path}")
            return True
        else:
            print(f"- No changes: {file_path}")
            return False
    except Exception as e:
        print(f"✗ Error processing {file_path}: {e}")
        return False

def process_directory(directory):
    """Process all XML files in a directory"""
    updated_count = 0
    
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.endswith('.xml'):
                file_path = os.path.join(root, file)
                if replace_colors_in_file(file_path):
                    updated_count += 1
    
    return updated_count

if __name__ == "__main__":
    print("Starting UI color updates...")
    
    # Process app module
    app_dir = r"C:\Users\HP\Downloads\Operit\app\src\main\res\layout"
    if os.path.exists(app_dir):
        count = process_directory(app_dir)
        print(f"\n✓ Updated {count} files in app module")
    
    # Process blurr module
    blurr_dir = r"C:\Users\HP\Downloads\Operit\blurr\app\src\main\res\layout"
    if os.path.exists(blurr_dir):
        count = process_directory(blurr_dir)
        print(f"\n✓ Updated {count} files in blurr module")
    
    print("\nDone!")
