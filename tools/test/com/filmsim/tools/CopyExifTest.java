package com.filmsim.tools;

import com.filmsim.app.CopyExif;
import com.filmsim.app.Develop;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public final class CopyExifTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static int shortAt(byte[] b, int p) { return (b[p] & 255) | ((b[p+1] & 255) << 8); }
    private static int intAt(byte[] b, int p) { return shortAt(b,p) | (shortAt(b,p+2) << 16); }
    private static void put(byte[] b, int p, int v, int count, boolean little) {
        for (int i=0;i<count;i++) b[p+(little?i:count-i-1)] = (byte)(v >> (8*i));
    }
    private static int entry(byte[] b, int offset, int tag) {
        for (int i=0;i<shortAt(b,offset);i++) {
            int p=offset+2+i*12; if (shortAt(b,p)==tag) return p;
        }
        return -1;
    }
    private static void validate(byte[] app, byte[] thumbnail) throws Exception {
        check((((app[2]&255)<<8)|(app[3]&255))==app.length-2, "APP1 length");
        byte[] t=Arrays.copyOfRange(app,10,app.length);
        int main=intAt(t,4), exif=intAt(t,entry(t,main,0x8769)+8);
        check(intAt(t,entry(t,exif,0xa002)+8)==1500, "actual width");
        check(intAt(t,entry(t,exif,0xa003)+8)==1000, "actual height");
        check(entry(t,exif,0x927c)==-1, "no stale MakerNotes");
        int interop=intAt(t,entry(t,exif,0xa005)+8);
        int index=entry(t,interop,1), version=entry(t,interop,2);
        check(t[index+8]=='R' && t[index+9]=='9' && t[index+10]=='8' && t[index+11]==0,"DCF R98 index");
        check(t[version+8]=='0' && t[version+9]=='1' && t[version+10]=='0' && t[version+11]=='0',"DCF interoperability version");
        int thumb=intAt(t,main+2+shortAt(t,main)*12);
        int start=intAt(t,entry(t,thumb,0x201)+8), length=intAt(t,entry(t,thumb,0x202)+8);
        check(Arrays.equals(thumbnail,Arrays.copyOfRange(t,start,start+length)), "fresh thumbnail bytes and valid offset");
        java.awt.image.BufferedImage decoded = javax.imageio.ImageIO.read(new ByteArrayInputStream(t,start,length));
        check(decoded != null && decoded.getWidth()==160 && decoded.getHeight()==120, "embedded thumbnail decodes");
        ByteArrayOutputStream jpeg=new ByteArrayOutputStream();jpeg.write(255);jpeg.write(216);jpeg.write(app);
        check(Arrays.equals(app,Develop.readExif(new ByteArrayInputStream(jpeg.toByteArray()))), "EXIF reader round trip");
    }
    public static void main(String[] args) throws Exception {
        byte[] thumbnail = null;
        for (int[] dimensions : new int[][]{{160,106},{80,120},{160,90}}) {
            int width = dimensions[0], height = dimensions[1];
            int[] pixels = new int[width * height]; Arrays.fill(pixels, 0xffee8844);
            thumbnail = CopyExif.thumbnail(pixels, width, height);
            java.awt.image.BufferedImage preview = javax.imageio.ImageIO.read(new ByteArrayInputStream(thumbnail));
            check(preview.getWidth()==160 && preview.getHeight()==120,"fixed DCF canvas");
            check((preview.getRGB(0,0)&0xffffff)<0x101010,"black padding");
            int center = preview.getRGB(80,60);
            check(Math.abs(((center>>16)&255)-238)<10 && Math.abs(((center>>8)&255)-136)<10,
                    "4:2:2 encoder preserves image colour");
            boolean frame = false;
            for (int pos=2; pos<thumbnail.length;) {
                int marker=thumbnail[pos+1]&255;
                int length=((thumbnail[pos+2]&255)<<8)|(thumbnail[pos+3]&255);
                if (marker==192) { check(thumbnail[pos+11]==0x21,"4:2:2 luma sampling");frame=true; }
                if (marker==218) break;
                check(marker!=221,"no restart interval");
                pos+=2+length;
            }
            check(frame,"baseline thumbnail frame exists");
        }
        int[] quadrants = new int[160*120];
        for (int y=0;y<120;y++) for (int x=0;x<160;x++)
            quadrants[y*160+x] = y<60 ? (x<80 ? 0xffee2020 : 0xff20ee20) : (x<80 ? 0xff2020ee : 0xffeeeeee);
        java.awt.image.BufferedImage decodedQuadrants = javax.imageio.ImageIO.read(
                new ByteArrayInputStream(CopyExif.thumbnail(quadrants,160,120)));
        for (int y : new int[]{24,96}) for (int x : new int[]{24,136}) {
            int expected=quadrants[y*160+x], actual=decodedQuadrants.getRGB(x,y);
            for (int shift : new int[]{0,8,16}) check(Math.abs(((actual>>shift)&255)-((expected>>shift)&255))<12,
                    "4:2:2 MCU order and vertical chroma placement");
        }
        validate(CopyExif.build(null,1500,1000,thumbnail),thumbnail);
        for (boolean little : new boolean[]{true,false}) {
            // Tiny TIFF: main orientation + Exif pointer, stale dimensions, exposure and MakerNote.
            byte[] app=new byte[128];app[0]=(byte)255;app[1]=(byte)225;
            byte[] sig={'E','x','i','f',0,0};System.arraycopy(sig,0,app,4,6);
            int t=10;app[t]=app[t+1]=(byte)(little?'I':'M');put(app,t+2,42,2,little);put(app,t+4,8,4,little);
            put(app,t+8,2,2,little);
            put(app,t+10,0x112,2,little);put(app,t+12,3,2,little);put(app,t+14,1,4,little);put(app,t+18,6,2,little);
            put(app,t+22,0x8769,2,little);put(app,t+24,4,2,little);put(app,t+26,1,4,little);put(app,t+30,38,4,little);
            put(app,t+38,4,2,little);
            int[] tags={0xa002,0xa003,0x8827,0x927c}, values={6000,4000,800,123};
            for(int i=0;i<4;i++) {
                int p=t+40+i*12;put(app,p,tags[i],2,little);put(app,p+2,4,2,little);put(app,p+4,1,4,little);put(app,p+8,values[i],4,little);
            }
            byte[] result=CopyExif.build(app,1500,1000,thumbnail);validate(result,thumbnail);
            byte[] out=Arrays.copyOfRange(result,10,result.length);int main=intAt(out,4), exif=intAt(out,entry(out,main,0x8769)+8);
            check(shortAt(out,entry(out,main,0x112)+8)==6,"preserve portrait orientation");
            check(intAt(out,entry(out,exif,0x8827)+8)==800,"preserve ISO across byte orders");
        }
        try { CopyExif.build(new byte[12],1500,1000,thumbnail);throw new AssertionError("malformed EXIF accepted"); }
        catch (java.io.IOException expected) { }
        if (args.length==2) {
            byte[] original=Develop.readExif(new FileInputStream(args[0]));
            byte[] result=CopyExif.build(original,1500,1000,thumbnail);validate(result,thumbnail);
            Files.write(Paths.get(args[1]),result);
            System.out.println("PASS actual Sony EXIF parsed and rebuilt (metadata fixture only)");
        }
        System.out.println("PASS copy EXIF: dimensions, thumbnail offsets, endian conversion, orientation, ISO, MakerNote removal, malformed input");
    }
}
