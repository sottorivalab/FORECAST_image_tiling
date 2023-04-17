import czireader.CZIReader;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.stream.IntStream;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import loci.formats.FormatException;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.CvException;

public class GenerateCZITiles {
    public static void main(String[] args) throws FormatException, IOException {
        int[] blockSize = new int[]{2000, 2000};
        int[] subBlockSize = new int[]{1000, 1000};
        String tilePath = "./tiles";
        int[] tileRange = new int[]{0, 1};
        boolean para = false;
        int resolution = 1;
        double inMPP = 0.11;
        double outMPP = 0.11;
        Double[] sharpParams = new Double[]{null, null};
        Double[] curve = new Double[]{0.0, 1.0, 0.45}; //To do, extract curve data from file for default
        
        for (int i=0; i<args.length-1; i++) {
            switch (args[i]) {
                case "-b":
                    blockSize[0] = Integer.parseInt(args[i+1]);
                    blockSize[1] = Integer.parseInt(args[i+2]);
                    i = i+2;
                    break;
                case "-c":
                    curve[0] = Double.parseDouble(args[i+1]);
                    curve[1] = Double.parseDouble(args[i+2]);
                    curve[2] = Double.parseDouble(args[i+3]);
                    i = i+3;
                    break;
                case "-s":
                    subBlockSize[0] = Integer.parseInt(args[i+1]);
                    subBlockSize[1] = Integer.parseInt(args[i+2]);
                    i = i+2;
                    break;
                case "-i":
                    inMPP = Double.parseDouble(args[i+1]);
                    i = i+1;
                    break;
                case "-o":
                    outMPP = Double.parseDouble(args[i+1]);
                    i = i+1;
                    break;
                case "-f":
                    tilePath = args[i+1];
                    i = i+1;
                    break;
                case "-r":
                    resolution = Integer.parseInt(args[i+1]);
                    i = i+1;
                    break;
                case "-q":
                    tileRange[0] = Integer.parseInt(args[i+1]);
                    tileRange[1] = Integer.parseInt(args[i+2]);
                    i = i+2;
                    break;
                case "-p":
                    para = true;
                    break;
                case "-u":
                    sharpParams[0] = Double.parseDouble(args[i+1]);
                    sharpParams[1] = Double.parseDouble(args[i+2]);
                    i = i+2;
                    break;
            }
        }
        
        if ((sharpParams[0] != null && sharpParams[1] != null && sharpParams[0] != 0 && sharpParams[1] != 0) || (curve[0] != 0.0 || curve[1] != 1.0 || curve[2] != 1.0)) {
            System.loadLibrary("opencv_java412");
        }
        
        String imagePath = args[args.length-1];
        
        CZIReader reader = new CZIReader(imagePath);
        int[] ImageSize = reader.getImageSize(resolution);
        double rescale = inMPP/outMPP;
        final int bits = reader.imageBPP();
        
        if (para) {
                reader.close();
        }
        
        new File(tilePath).mkdirs();
        
        int[] nBlocks = new int[]{(int)Math.ceil((ImageSize[0]*rescale)/blockSize[0]), (int)Math.ceil((ImageSize[1]*rescale)/blockSize[1])};
        
        long totalBlocks = nBlocks[0]*nBlocks[1];
        int start = (int)((totalBlocks*(long)tileRange[0])/tileRange[1]);
        int end = (int)((totalBlocks*(long)(tileRange[0]+1))/tileRange[1]);
       
        if (para) {
            String tP = tilePath;
            int res = resolution;
            IntStream.range(start, end).parallel().forEach(i->{
                writeImagePara(imagePath, tP, i, ImageSize, res, blockSize, subBlockSize, nBlocks, rescale, sharpParams, curve, bits);
            });
        } else { 
            for (int i=start; i<end; i++) {
                writeImageSerial(reader, tilePath, i, ImageSize, resolution, blockSize, subBlockSize, nBlocks, rescale, sharpParams, curve, bits);
            }
            
            reader.close();
        }
    }

    public static void writeImagePara(String imagePath, String folder, int index, int[] imageSize, int resolution, int[] blockSize, int[] subBlockSize, int[] nBlocks, double rescale, Double[] sharpParams,  Double[] curve, final int bits) {
        CZIReader reader;

        try {
            reader = new CZIReader(imagePath);
        } catch (FormatException fe) {
            fe.printStackTrace();
            return;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }
        
        double[] loadBlockSize = new double[]{(double)blockSize[0]/rescale, (double)blockSize[1]/rescale};

        int blockX = index%nBlocks[0];
        int blockY = index/nBlocks[0];

        int[] region = new int[]{(int)Math.round(loadBlockSize[0]*blockX), (int)Math.round(loadBlockSize[1]*blockY)};
        int[] regionEnd = new int[]{(int)Math.round(loadBlockSize[0]*(blockX+1)), (int)Math.round(loadBlockSize[1]*(blockY+1))};
        
        int[] regionExcess = new int[]{Math.max(0, regionEnd[0]-imageSize[0]), Math.max(0, regionEnd[1]-imageSize[1])};
        
        int[] inBlockSize = new int[]{regionEnd[0]-regionExcess[0]-region[0], regionEnd[1]-regionExcess[1]-region[1]};
        int[] outBlockSize = new int[]{(int)Math.round((double)blockSize[0]-((double)regionExcess[0]*rescale)), (int)Math.round((double)blockSize[1]-((double)regionExcess[1]*rescale))};
        
        BufferedImage img = new BufferedImage(inBlockSize[0], inBlockSize[1], BufferedImage.TYPE_INT_RGB);

        if (bits==8) {
            for (int i=0; i<inBlockSize[0]; i=i+subBlockSize[0]) {
                for (int j=0; j<inBlockSize[1]; j=j+subBlockSize[1]) {
                    readIntoBuffer8Bit(reader, img, resolution, region, new int[]{i, j}, new int[]{Math.min(subBlockSize[0], inBlockSize[0]-i), Math.min(subBlockSize[1], inBlockSize[1]-j)});
                }
            }
        } else if (bits==10) {
            for (int i=0; i<inBlockSize[0]; i=i+subBlockSize[0]) {
                for (int j=0; j<inBlockSize[1]; j=j+subBlockSize[1]) {
                    readIntoBuffer10Bit(reader, img, resolution, region, new int[]{i, j}, new int[]{Math.min(subBlockSize[0], inBlockSize[0]-i), Math.min(subBlockSize[1], inBlockSize[1]-j)});
                }
            }
        } else if (bits==12) {
            for (int i=0; i<inBlockSize[0]; i=i+subBlockSize[0]) {
                for (int j=0; j<inBlockSize[1]; j=j+subBlockSize[1]) {
                    readIntoBuffer12Bit(reader, img, resolution, region, new int[]{i, j}, new int[]{Math.min(subBlockSize[0], inBlockSize[0]-i), Math.min(subBlockSize[1], inBlockSize[1]-j)});
                }
            }
        } else {
            System.out.println("Bad Image Bitness.");
            
            try {
                reader.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            
            return;
        }
        
        try {
            reader.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        try {
            if ((sharpParams[0] != null && sharpParams[1] != null && sharpParams[0] != 0 && sharpParams[1] != 0) || (curve[0] != 0.0 || curve[1] != 1.0 || curve[2] != 1.0)) {
                img = applyCurveAndSharpen(img, curve, sharpParams);
            }

            if (inBlockSize[0] != outBlockSize[0] || inBlockSize[1] != outBlockSize[1]) {
                img = resize(img, outBlockSize);
            }

            ImageIO.write(img, "jpg", Paths.get(folder, "Da"+index+".jpg").toFile());
            
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }
    }

    public static void writeImageSerial(CZIReader reader, String folder, int index, int[] imageSize, int resolution, int[] blockSize, int[] subBlockSize, int[] nBlocks, double rescale, Double[] sharpParams, Double[] curve, final int bits) {
        double[] loadBlockSize = new double[]{(double)blockSize[0]/rescale, (double)blockSize[1]/rescale};

        int blockX = index%nBlocks[0];
        int blockY = index/nBlocks[0];

        int[] region = new int[]{(int)Math.round(loadBlockSize[0]*blockX), (int)Math.round(loadBlockSize[1]*blockY)};
        int[] regionEnd = new int[]{(int)Math.round(loadBlockSize[0]*(blockX+1)), (int)Math.round(loadBlockSize[1]*(blockY+1))};
        
        int[] regionExcess = new int[]{Math.max(0, regionEnd[0]-imageSize[0]), Math.max(0, regionEnd[1]-imageSize[1])};
        
        int[] inBlockSize = new int[]{regionEnd[0]-regionExcess[0]-region[0], regionEnd[1]-regionExcess[1]-region[1]};
        int[] outBlockSize = new int[]{(int)Math.round((double)blockSize[0]-((double)regionExcess[0]*rescale)), (int)Math.round((double)blockSize[1]-((double)regionExcess[1]*rescale))};
        
        BufferedImage img = new BufferedImage(inBlockSize[0], inBlockSize[1], BufferedImage.TYPE_INT_RGB);
        
        if (bits==8) {
            for (int i=0; i<inBlockSize[0]; i=i+subBlockSize[0]) {
                for (int j=0; j<inBlockSize[1]; j=j+subBlockSize[1]) {
                    readIntoBuffer8Bit(reader, img, resolution, region, new int[]{i, j}, new int[]{Math.min(subBlockSize[0], inBlockSize[0]-i), Math.min(subBlockSize[1], inBlockSize[1]-j)});
                }
            }
        } else if (bits==10) {
            for (int i=0; i<inBlockSize[0]; i=i+subBlockSize[0]) {
                for (int j=0; j<inBlockSize[1]; j=j+subBlockSize[1]) {
                    readIntoBuffer10Bit(reader, img, resolution, region, new int[]{i, j}, new int[]{Math.min(subBlockSize[0], inBlockSize[0]-i), Math.min(subBlockSize[1], inBlockSize[1]-j)});
                }
            }
        } else if (bits==12) {
            for (int i=0; i<inBlockSize[0]; i=i+subBlockSize[0]) {
                for (int j=0; j<inBlockSize[1]; j=j+subBlockSize[1]) {
                    readIntoBuffer12Bit(reader, img, resolution, region, new int[]{i, j}, new int[]{Math.min(subBlockSize[0], inBlockSize[0]-i), Math.min(subBlockSize[1], inBlockSize[1]-j)});
                }
            }
        } else {
            System.out.println("Bad Image Bitness.");
            
            try {
                reader.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            
            return;
        }
        
        try {
            reader.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        
        try {
            if ((sharpParams[0] != null && sharpParams[1] != null && sharpParams[0] != 0 && sharpParams[1] != 0) && (curve[0] != 0.0 || curve[1] != 1.0 || curve[2] != 1.0)) {
                img = applyCurveAndSharpen(img, curve, sharpParams);
            }
            
            if (inBlockSize[0] != outBlockSize[0] || inBlockSize[1] != outBlockSize[1]) {
                img = resize(img, outBlockSize);
            }
            
            ImageIO.write(img, "jpg", Paths.get(folder, "Da"+index+".jpg").toFile());
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }
    }
    
    public static void readIntoBuffer8Bit(CZIReader reader, BufferedImage img, int resolution, int[] region, int[] subRegion, int[] blockSize) {
        byte[] data;
        
        try {
            data = reader.getImageRegion(resolution, region[0]+subRegion[0], region[1]+subRegion[1], blockSize[0], blockSize[1]);
        } catch (FormatException fe) {
            fe.printStackTrace();
            return;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }
        
        for(int r=0; r<blockSize[1]; r++) {
            for(int c=0; c<blockSize[0]; c++) {
                int red=data[3*((r*blockSize[0])+c)]&0xFF;
                int green=data[3*((r*blockSize[0])+c)+1]&0xFF;
                int blue=data[3*((r*blockSize[0])+c)+2]&0xFF;
                    
                int rgb = (red << 16) | (green << 8) | blue;
                img.setRGB(subRegion[0]+c, subRegion[1]+r, rgb);
            }
        }
    }

    public static void readIntoBuffer10Bit(CZIReader reader, BufferedImage img, int resolution, int[] region, int[] subRegion, int[] blockSize) {
        byte[] data;
        
        try {
            data = reader.getImageRegion(resolution, region[0]+subRegion[0], region[1]+subRegion[1], blockSize[0], blockSize[1]);
        } catch (FormatException fe) {
            fe.printStackTrace();
            return;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }
        
        for(int r=0; r<blockSize[1]; r++) {
            for(int c=0; c<blockSize[0]; c++) {
                int red=((data[2*3*((r*blockSize[0])+c)]&0xFF) | ((data[2*3*((r*blockSize[0])+c)+1]&0xFF) << 8)) >> 2;
                int green=((data[2*(3*((r*blockSize[0])+c)+1)]&0xFF) | ((data[2*(3*((r*blockSize[0])+c)+1)+1]&0xFF) << 8)) >> 2;
                int blue=((data[2*(3*((r*blockSize[0])+c)+2)]&0xFF) | ((data[2*(3*((r*blockSize[0])+c)+2)+1]&0xFF) << 8)) >> 2;
                    
                int rgb = (red << 16) | (green << 8) | blue;
                img.setRGB(subRegion[0]+c, subRegion[1]+r, rgb);
            }
        }
    }
    
    public static void readIntoBuffer12Bit(CZIReader reader, BufferedImage img, int resolution, int[] region, int[] subRegion, int[] blockSize)  {
        byte[] data;
        
        try {
            data = reader.getImageRegion(resolution, region[0]+subRegion[0], region[1]+subRegion[1], blockSize[0], blockSize[1]);
        } catch (FormatException fe) {
            fe.printStackTrace();
            return;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }
        
        for(int r=0; r<blockSize[1]; r++) {
            for(int c=0; c<blockSize[0]; c++) {
                int red=((data[2*3*((r*blockSize[0])+c)]&0xFF) | ((data[2*3*((r*blockSize[0])+c)+1]&0xFF) << 8)) >> 4;
                int green=((data[2*(3*((r*blockSize[0])+c)+1)]&0xFF) | ((data[2*(3*((r*blockSize[0])+c)+1)+1]&0xFF) << 8)) >> 4;
                int blue=((data[2*(3*((r*blockSize[0])+c)+2)]&0xFF) | ((data[2*(3*((r*blockSize[0])+c)+2)+1]&0xFF) << 8)) >> 4;
                    
                int rgb = (red << 16) | (green << 8) | blue;
                img.setRGB(subRegion[0]+c, subRegion[1]+r, rgb);
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
    
    private static synchronized BufferedImage applyCurveAndSharpen(BufferedImage img, Double[] curve, Double[] sharpParams) {
        BufferedImage byteImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2d = byteImg.createGraphics();
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
    
        Mat imgMat = new Mat(byteImg.getHeight(), byteImg.getWidth(), CvType.CV_8UC3);
        byte[] inData = ((DataBufferByte)byteImg.getRaster().getDataBuffer()).getData();
        imgMat.put(0, 0, inData);
        
        if (curve[0] != 0 || curve[1] != 1 || curve[2] != 1) {
            imgMat.convertTo(imgMat, CvType.CV_32F, 1.0/(255.0*(curve[1]-curve[0])), -curve[0]/(255.0*(curve[1]-curve[0])));
            
            if (curve[2] != 1) {
                Core.pow(imgMat, curve[2], imgMat);
            }
            
        } else {
            imgMat.convertTo(imgMat, CvType.CV_32F, 1.0/255.0, 0);
        }
        
        if (sharpParams[0] != null && sharpParams[1] != null && sharpParams[0] != 0 && sharpParams[1] != 0) {
            ArrayList<Mat> labChannels = new ArrayList<Mat>();
            Imgproc.cvtColor(imgMat, imgMat, Imgproc.COLOR_BGR2Lab);
            Core.split(imgMat, labChannels);
        
            Mat sharpL = new Mat(byteImg.getHeight(), byteImg.getWidth(), CvType.CV_32F);
            Imgproc.GaussianBlur(labChannels.get(0), sharpL, new Size(0, 0), sharpParams[0]);
            Core.addWeighted(labChannels.get(0), sharpParams[1]+1, sharpL, -sharpParams[1], 0, labChannels.get(0));
            sharpL.release();

	        //Core.merge seems to throw assert exceptions in parallel mode
            //for some reason. It's rare and seemingly happens at random.
            //To fix this, catch the exception and try again. If the exception
            //gets thrown 100 times in a row, it's probably a different issue,
            //so rethrow.
            int aCount = 0;

            while (aCount < 100) {
                try {
                    Core.merge(labChannels, imgMat);
                    break;
                } catch (CvException cve) {
                    if (aCount == 100) {
                        throw(cve);
                    } else {
                        aCount++;
                    }
                }
            }

            Imgproc.cvtColor(imgMat, imgMat, Imgproc.COLOR_Lab2BGR);
        
        }
        
        imgMat.convertTo(imgMat, CvType.CV_8UC3, 255, 0);
        
        BufferedImage outImage = new BufferedImage(byteImg.getWidth(), byteImg.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        byte[] outData = ((DataBufferByte) outImage.getRaster().getDataBuffer()).getData();
        imgMat.get(0, 0, outData);
        imgMat.release();
        return outImage;
    }
}
