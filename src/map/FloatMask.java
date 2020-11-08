package map;

import java.util.Random;

public class FloatMask extends Mask {
//    public BufferedImage img;
    public int size = 512;
    public float[] mask;
//    public WritableRaster rast;
    public String name;

    public FloatMask(int size, String name) {
//        img = new BufferedImage(size, size, BufferedImage.TYPE_USHORT_GRAY);
//        rast = img.getRaster();
        this.size = size;
        this.name = name;
        mask = new float[size * size];
        init();
    }

//    public void makeImg() {
//        float[] multipliedMask = new float[size*size];
//        for (int i=0; i<size*size; i++) {
//            multipliedMask[i] = mask[i] * 65535;
//        }
//        rast.setSamples(0, 0, size, size, 0, multipliedMask);
//    }

    private FloatMask init() {
        Random rand = new Random();
        int[][] blobs = new int[8][3];
        for (int n = 0; n < blobs.length; n++) {
            blobs[n][0] = rand.nextInt(size);
            blobs[n][1] = rand.nextInt(size);
            blobs[n][2] = rand.nextInt(size);
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {

                for (int n = 0; n < blobs.length; n++) {
                    double distanceToThisBlob = Math.sqrt(
                            (blobs[n][0] - x) * (blobs[n][0] - x) +
                                    (blobs[n][1] - y) * (blobs[n][1] - y)
                    );

                    if (distanceToThisBlob < 0.3f*blobs[n][2]) {
                        mask[y * size + x] = 1;
                    }
                }
//                if ((1 - (distanceToBlobs / size)) > 0.9) {
//                    mask[y * size + x] = 1;
//                } else {
//                    mask[y * size + x] = 0;
//                }
            }
        }
        return this;
    }

}

