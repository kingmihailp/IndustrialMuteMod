#!/usr/bin/env python3
"""Generate camera.png texture (64x32) for the URL Camera mod."""

import struct
import zlib

def create_png(width, height, pixels):
    """Create a PNG file from pixel data.
    pixels: list of (r, g, b, a) tuples, row by row top-left to bottom-right
    """
    def chunk(chunk_type, data):
        c = chunk_type + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xFFFFFFFF)

    # PNG signature
    sig = b'\x89PNG\r\n\x1a\n'

    # IHDR
    ihdr_data = struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)
    # bit depth=8, color type=2 (RGB), compression=0, filter=0, interlace=0
    # Actually use RGBA (color type 6) for alpha support
    ihdr_data = struct.pack('>II', width, height) + bytes([8, 6, 0, 0, 0])
    ihdr = chunk(b'IHDR', ihdr_data)

    # Image data
    raw_rows = []
    for y in range(height):
        row_data = b'\x00'  # filter type None
        for x in range(width):
            r, g, b, a = pixels[y * width + x]
            row_data += bytes([r, g, b, a])
        raw_rows.append(row_data)

    raw_data = b''.join(raw_rows)
    compressed = zlib.compress(raw_data, 9)
    idat = chunk(b'IDAT', compressed)

    # IEND
    iend = chunk(b'IEND', b'')

    return sig + ihdr + idat + iend


def main():
    width, height = 64, 32

    # Initialize all pixels as transparent black
    pixels = [(0, 0, 0, 0)] * (width * height)

    def set_pixel(x, y, r, g, b, a=255):
        if 0 <= x < width and 0 <= y < height:
            pixels[y * width + x] = (r, g, b, a)

    def fill_rect(x1, y1, w, h, r, g, b, a=255):
        for dy in range(h):
            for dx in range(w):
                set_pixel(x1 + dx, y1 + dy, r, g, b, a)

    # Camera body UV layout for an 8x5x6 box:
    # texOffs(0, 0), addBox(-4, -2.5, -3, 8, 5, 6)
    # Standard MC box UV layout (starting at u=0, v=0):
    # Each face layout:
    #   top:    (6,0) to (14, 6)       = (tw=8, th=6) at offset (6,0)
    #   bottom: (14,0) to (22,6)       = (8,6) at (14,0)
    #   right:  (0,6) to (6,11)        = (6,5)  at (0,6)
    #   front:  (6,6) to (14,11)       = (8,5)  at (6,6)
    #   left:   (14,6) to (20,11)      = (6,5)  at (14,6)
    #   back:   (20,6) to (28,11)      = (8,5)  at (20,6)
    # For box(xSize=8, ySize=5, zSize=6), texOffs(0,0):
    #   top-left of texture layout:
    #     top face:    at (zSize, 0)      = (6, 0),   size (xSize, zSize) = (8, 6)
    #     bottom face: at (zSize+xSize,0) = (14, 0),  size (xSize, zSize) = (8, 6)
    #     right face:  at (0, zSize)      = (0, 6),   size (zSize, ySize) = (6, 5)
    #     front face:  at (zSize, zSize)  = (6, 6),   size (xSize, ySize) = (8, 5)
    #     left face:   at (zSize+xSize, zSize) = (14, 6), size (zSize, ySize) = (6, 5)
    #     back face:   at (zSize+xSize+zSize, zSize) = (20, 6), size (xSize, ySize) = (8, 5)

    DARK_GRAY = (40, 40, 40)
    DARKER = (25, 25, 25)
    FRONT = (20, 20, 20)
    BLACK = (5, 5, 5)
    LENS_OUTER = (15, 15, 15)
    LENS_INNER = (3, 3, 3)
    METAL = (55, 55, 60)
    SCREW = (70, 70, 70)

    # Body top face: (6,0) 8x6
    fill_rect(6, 0, 8, 6, *DARK_GRAY)
    # Add some detail lines
    for x in range(6, 14):
        set_pixel(x, 0, *METAL)
        set_pixel(x, 5, *DARKER)

    # Body bottom face: (14,0) 8x6
    fill_rect(14, 0, 8, 6, *DARK_GRAY)
    for x in range(14, 22):
        set_pixel(x, 0, *METAL)

    # Body right face: (0,6) 6x5
    fill_rect(0, 6, 6, 5, *DARK_GRAY)
    # screw detail
    set_pixel(1, 7, *SCREW)
    set_pixel(1, 9, *SCREW)

    # Body front face: (6,6) 8x5 - darker with lens mount area
    fill_rect(6, 6, 8, 5, *FRONT)
    # Lens opening circle approximation in center
    fill_rect(8, 7, 4, 3, *BLACK)
    # Lens ring
    set_pixel(8, 7, *LENS_OUTER)
    set_pixel(11, 7, *LENS_OUTER)
    set_pixel(8, 9, *LENS_OUTER)
    set_pixel(11, 9, *LENS_OUTER)
    # Center lens
    fill_rect(9, 8, 2, 1, *LENS_INNER)

    # Body left face: (14,6) 6x5
    fill_rect(14, 6, 6, 5, *DARK_GRAY)
    set_pixel(18, 7, *SCREW)
    set_pixel(18, 9, *SCREW)

    # Body back face: (20,6) 8x5
    fill_rect(20, 6, 8, 5, *DARKER)
    # Cable port detail
    fill_rect(22, 8, 3, 2, *BLACK)
    # LED indicator
    set_pixel(26, 7, 180, 30, 30)  # red LED

    # Lens UV layout for box(4, 4, 3), texOffs(28, 0):
    # xSize=4, ySize=4, zSize=3
    # top face:    at (28+3, 0)   = (31, 0), size (4, 3) = (4, 3)
    # bottom face: at (28+3+4, 0) = (35, 0), size (4, 3)
    # right face:  at (28, 3)     = (28, 3), size (3, 4)
    # front face:  at (28+3, 3)   = (31, 3), size (4, 4)
    # left face:   at (28+3+4, 3) = (35, 3), size (3, 4)
    # back face:   at (28+3+4+3,3)= (38, 3), size (4, 4)

    # Lens top: (31,0) 4x3
    fill_rect(31, 0, 4, 3, *LENS_OUTER)

    # Lens bottom: (35,0) 4x3
    fill_rect(35, 0, 4, 3, *LENS_OUTER)

    # Lens right: (28,3) 3x4
    fill_rect(28, 3, 3, 4, *LENS_OUTER)

    # Lens front: (31,3) 4x4 - the front face of lens (glass)
    fill_rect(31, 3, 4, 4, *LENS_INNER)
    # Glass reflection
    set_pixel(31, 3, 30, 50, 80)
    set_pixel(32, 3, 20, 35, 60)
    set_pixel(31, 4, 20, 35, 60)

    # Lens left: (35,3) 3x4
    fill_rect(35, 3, 3, 4, *LENS_OUTER)

    # Lens back: (38,3) 4x4 (connection to body)
    fill_rect(38, 3, 4, 4, *DARK_GRAY)

    # Write the PNG
    png_data = create_png(width, height, pixels)
    output_path = 'src/main/resources/assets/urlcamera/textures/entity/camera.png'
    with open(output_path, 'wb') as f:
        f.write(png_data)

    print(f"Texture created: {output_path} ({width}x{height}, {len(png_data)} bytes)")


if __name__ == '__main__':
    main()
