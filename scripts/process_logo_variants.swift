import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

enum LogoProcessingError: Error {
    case invalidArguments
    case unreadableImage
    case contextCreationFailed
    case exportFailed
}

func loadImage(at path: String) throws -> CGImage {
    let url = URL(fileURLWithPath: path) as CFURL
    guard let source = CGImageSourceCreateWithURL(url, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
        throw LogoProcessingError.unreadableImage
    }
    return image
}

func savePng(_ image: CGImage, to path: String) throws {
    let url = URL(fileURLWithPath: path) as CFURL
    guard let destination = CGImageDestinationCreateWithURL(url, UTType.png.identifier as CFString, 1, nil) else {
        throw LogoProcessingError.exportFailed
    }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else {
        throw LogoProcessingError.exportFailed
    }
}

func processedLogo(from image: CGImage, white: Bool) throws -> CGImage {
    let width = image.width
    let height = image.height
    let bytesPerPixel = 4
    let bytesPerRow = width * bytesPerPixel
    var pixels = [UInt8](repeating: 0, count: height * bytesPerRow)
    var removedPixelCount = 0

    guard let context = CGContext(
        data: &pixels,
        width: width,
        height: height,
        bitsPerComponent: 8,
        bytesPerRow: bytesPerRow,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    ) else {
        throw LogoProcessingError.contextCreationFailed
    }

    context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))

    for offset in stride(from: 0, to: pixels.count, by: bytesPerPixel) {
        let red = Int(pixels[offset])
        let green = Int(pixels[offset + 1])
        let blue = Int(pixels[offset + 2])
        let alpha = pixels[offset + 3]

        if alpha > 0 && red < 72 && green < 72 && blue < 72 {
            pixels[offset] = 0
            pixels[offset + 1] = 0
            pixels[offset + 2] = 0
            pixels[offset + 3] = 0
            removedPixelCount += 1
            continue
        }

        if white && alpha > 0 {
            pixels[offset] = alpha
            pixels[offset + 1] = alpha
            pixels[offset + 2] = alpha
        }
    }

    guard let output = context.makeImage() else {
        throw LogoProcessingError.contextCreationFailed
    }
    print("Removed dark pixels for transparent LAB cutout: \(removedPixelCount); white variant: \(white)")
    return output
}

guard CommandLine.arguments.count == 4 else {
    throw LogoProcessingError.invalidArguments
}

let input = try loadImage(at: CommandLine.arguments[1])
try savePng(try processedLogo(from: input, white: false), to: CommandLine.arguments[2])
try savePng(try processedLogo(from: input, white: true), to: CommandLine.arguments[3])
