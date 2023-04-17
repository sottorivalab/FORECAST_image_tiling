import czireader.CZIReader;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import loci.formats.FormatException;

public class GenerateCZITileMetadata {
    public static void main(String args[]) throws FormatException, IOException, IllegalArgumentException {
        int[] blockSize = new int[]{2000, 2000};
        String tilePath = "./tiles";
        int resolution = 1;
	    double inMPP = 0.11;
	    double outMPP = 0.11;
	    int thumbWidth = 1024;
	    double ss1Magnification = 1.25;
        
        for (int i=0; i<args.length-1; i++) {
            switch (args[i]) {
                case "-b":
                    blockSize[0] = Integer.parseInt(args[i+1]);
                    blockSize[1] = Integer.parseInt(args[i+2]);
                    i = i+2;
                    break;
                case "-f":
                    tilePath = args[i+1];
                    i = i+1;
                    break;
                case "-r":
                    resolution = Integer.parseInt(args[i+1]);
                    i = i+1;
                    break;
                case "-i":
                    inMPP = Double.parseDouble(args[i+1]);
                    i = i+1;
                    break;
                case "-o":
                    outMPP = Double.parseDouble(args[i+1]);
                    i = i+1;
                    break;
                case "-w":
                    thumbWidth = Integer.parseInt(args[i+1]);
                    i = i+1;
                    break;
                case "-m":
                    ss1Magnification = Double.parseDouble(args[i+1]);
                    i = i+1;
                    break;
            }
        }
        
        String imagePath = args[args.length-1];
        
        new File(tilePath).mkdirs();
        
        CZIReader reader = new CZIReader(imagePath);
        
        double rescale = inMPP/outMPP;
        int[] imageSize = reader.getImageSize(resolution);
        imageSize[0] = (int)Math.ceil(imageSize[0]*rescale);
        imageSize[1] = (int)Math.ceil(imageSize[1]*rescale);
        
        generateSlideThumb(reader, tilePath, thumbWidth);
        generateSS1(reader, tilePath, ss1Magnification, rescale);
        
        reader.close();
        
        generateFinalScan(tilePath, imageSize, blockSize);
        generateParamFile(tilePath, imagePath, imageSize, blockSize, resolution);
    }
        
    public static void generateFinalScan(String tilePath, int[] imageSize, int[] blockSize) throws IOException {         
        int[] nBlocks = new int[]{(imageSize[0]+blockSize[0]-1)/blockSize[0], (imageSize[1]+blockSize[0]-1)/blockSize[1]};
        double scaleFactor = 4;

        String[] lines = new String[4*nBlocks[0]*nBlocks[1] + 11];
        lines[0] = "[Header]";
        lines[1] = "iImageWidth=" + blockSize[0];
        lines[2] = "iImageHeight=" + blockSize[1];
        lines[3] = "lXStepSize=" + (int)(scaleFactor*blockSize[0]);
        lines[4] = "lYStepSize=" + (int)(scaleFactor*blockSize[1]);
        lines[5] = "tImageType=.jpg";
        lines[6] = "tDescription=      " + imageSize[0] + "x" + imageSize[1];
        
        lines[7] = "[Level0]";
        lines[8] = "iZoom=1";
        lines[9] = "iWidth="+imageSize[0];
        lines[10] = "iHeight="+imageSize[1];
        
        for (int i=0; i<nBlocks[0]; i++) {
            for (int j=0; j<nBlocks[1]; j++) {
                int idx = j*nBlocks[0] + i;
                int X = (imageSize[0]/2) - (i*blockSize[0]) - (blockSize[0]/2);
                int Y = (imageSize[1]/2) - (j*blockSize[1]) - (blockSize[1]/2);
                
                lines[4*idx + 11] = "[Da" + idx + "]";
                lines[4*idx + 12] = "x=" + (4*X);
                lines[4*idx + 13] = "y=" + (4*Y);
                lines[4*idx + 14] = "z=0";
            }
        }
        
        Files.write(Paths.get(tilePath, "FinalScan.ini"), Arrays.asList(lines), StandardCharsets.UTF_8);
    }
    
    public static void generateSlideThumb(CZIReader reader, String tilePath, int thumbWidth) throws FormatException, IOException, IllegalArgumentException {
        int[] imageSize = reader.getImageSize(0);
        int loadResolution = (int)((Math.log(imageSize[0])-Math.log(thumbWidth))/Math.log(2));
        int[] loadThumbSize = reader.getImageSize(loadResolution);
        
        byte[] thumbData = reader.getImageRegion(loadResolution, 0, 0, loadThumbSize[0], loadThumbSize[1]);
        BufferedImage img = new BufferedImage(loadThumbSize[0], loadThumbSize[1], BufferedImage.TYPE_INT_RGB);
        
        int bits = reader.imageBPP();
        
        if (bits==8) {
            readIntoBuffer8Bit(thumbData, img, loadThumbSize);
        } else if (bits==10) {
            readIntoBuffer10Bit(thumbData, img, loadThumbSize);
        } else if (bits==12) {
            readIntoBuffer12Bit(thumbData, img, loadThumbSize);
        } else {
            throw new IllegalArgumentException("Bad Image Bitness.");
        }
        
        int[] outThumbSize = new int[]{thumbWidth, (int)Math.round((double)loadThumbSize[1]*((double)thumbWidth/(double)loadThumbSize[0]))};
        img = resize(img, outThumbSize);
        
        ImageIO.write(img,"jpg", Paths.get(tilePath, "SlideThumb.jpg").toFile());
    }
    
    public static void generateSS1(CZIReader reader, String tilePath, double magnification, double scale) throws FormatException, IOException, IllegalArgumentException {
        int loadResolution = (int)(Math.log(40.0/magnification)/Math.log(2.0));
        scale = scale*(magnification/(40.0*Math.pow(2, -loadResolution)));
    
        int[] loadSS1Size = reader.getImageSize(loadResolution);
        int[] outSS1Size = new int[]{(int)((double)loadSS1Size[0]*scale), (int)((double)loadSS1Size[1]*scale)};
        
        byte[] ss1Data = reader.getImageRegion(loadResolution, 0, 0, loadSS1Size[0], loadSS1Size[1]);
        BufferedImage img = new BufferedImage(loadSS1Size[0], loadSS1Size[1], BufferedImage.TYPE_INT_RGB);
        
        int bits = reader.imageBPP();
        
        if (bits==8) {
            readIntoBuffer8Bit(ss1Data, img, loadSS1Size);
        } else if (bits==10) {
            readIntoBuffer10Bit(ss1Data, img, loadSS1Size);
        } else if (bits==12) {
            readIntoBuffer12Bit(ss1Data, img, loadSS1Size);
        } else {
            throw new IllegalArgumentException("Bad Image Bitness.");
        }
        
        img = resize(img, outSS1Size);
        
        ImageIO.write(img,"jpg", Paths.get(tilePath, "Ss1.jpg").toFile());
    }
    
    public static void generateParamFile(String tilePath, String imagePath, int[] imageSize, int[] blockSize, int resolution) throws IOException {
        File ipFile = new File(imagePath);
        
        String[] fileContents = new String[]{ipFile.getName(), Integer.toString(imageSize[0]), Integer.toString(imageSize[1]), imagePath, Integer.toString(blockSize[0]), Integer.toString(blockSize[1]), Integer.toString((int)Math.pow(2, resolution)), Integer.toString((int)Math.round(40.0/Math.pow(2, resolution))), "40"};
        
        Files.write(Paths.get(tilePath, "param.txt"), Arrays.asList(fileContents), StandardCharsets.UTF_8);
    }
    
    public static void readIntoBuffer8Bit(byte[] data, BufferedImage img, int[] blockSize) {
        for(int r=0; r<blockSize[1]; r++) {
            for(int c=0; c<blockSize[0]; c++) {
                int red=data[3*((r*blockSize[0])+c)]&0xFF;
                int green=data[3*((r*blockSize[0])+c)+1]&0xFF;
                int blue=data[3*((r*blockSize[0])+c)+2]&0xFF;
                    
                int rgb = (red << 16) | (green << 8) | blue;
                img.setRGB(c, r, rgb);
            }
        }
    }
    
    public static void readIntoBuffer10Bit(byte[] data, BufferedImage img, int[] blockSize) {
        for(int r=0; r<blockSize[1]; r++) {
            for(int c=0; c<blockSize[0]; c++) {
                int red=((data[2*3*((r*blockSize[0])+c)]&0xFF) | ((data[2*3*((r*blockSize[0])+c)+1]&0xFF) << 8)) >> 2;
                int green=((data[2*(3*((r*blockSize[0])+c)+1)]&0xFF) | ((data[2*(3*((r*blockSize[0])+c)+1)+1]&0xFF) << 8)) >> 2;
                int blue=((data[2*(3*((r*blockSize[0])+c)+2)]&0xFF) | ((data[2*(3*((r*blockSize[0])+c)+2)+1]&0xFF) << 8)) >> 2;
                    
                int rgb = (red << 16) | (green << 8) | blue;
                img.setRGB(c, r, rgb);
            }
        }
    }
    
    public static void readIntoBuffer12Bit(byte[] data, BufferedImage img, int[] blockSize) {
        for(int r=0; r<blockSize[1]; r++) {
            for(int c=0; c<blockSize[0]; c++) {
                int red=((data[2*3*((r*blockSize[0])+c)]&0xFF) | ((data[2*3*((r*blockSize[0])+c)+1]&0xFF) << 8)) >> 4;
                int green=((data[2*(3*((r*blockSize[0])+c)+1)]&0xFF) | ((data[2*(3*((r*blockSize[0])+c)+1)+1]&0xFF) << 8)) >> 4;
                int blue=((data[2*(3*((r*blockSize[0])+c)+2)]&0xFF) | ((data[2*(3*((r*blockSize[0])+c)+2)+1]&0xFF) << 8)) >> 4;
                    
                int rgb = (red << 16) | (green << 8) | blue;
                img.setRGB(c, r, rgb);
            }
        }
    }
    
    private static BufferedImage resize(BufferedImage img, int[] outSize) {
        Image tmp = img.getScaledInstance(outSize[0], outSize[1], Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(outSize[0], outSize[1], BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return resized;
    }
}
