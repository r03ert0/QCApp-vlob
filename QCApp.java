import java.awt.Dimension;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.*;
import java.awt.geom.*;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import  javax.swing.GroupLayout.Alignment.*;

import java.io.*;
import java.nio.*;
import javax.imageio.*;

import java.util.Vector;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;
import java.util.InputMismatchException;
import java.util.regex.Pattern;

class MyVolume
{           
	byte	volume[];					// 3d volume data
	short	dim[]=new short[4];			// 3d volume dimensions (1st:dim[1])
	float	pixdim[]=new float[4];		// 3d volume pixel dimensions
	short	datatype;					// 3d volume data type
	int		boundingBox[]=new int[6];	// 3d volume bounding box

	int bytesPerVoxel()
	{
		int bpv=0;
		switch(datatype)
		{
			case 2:		bpv=1; break;//DT_UINT8
			case 4:		bpv=2; break;//DT_INT16
			case 8:		bpv=4; break;//DT_INT32
			case 16:	bpv=4; break;//DT_FLOAT32
		}
		return bpv;
	}
	int loadNiftiVolume(String filename)
	{
		int	err=0;
		
		try
		{
			// Read volume data
			FileInputStream	fis=new FileInputStream(filename/*"/tmp/t1w.nii"*/);
			GZIPInputStream gis = new GZIPInputStream(fis);
			DataInputStream	dis=new DataInputStream(gis);
			byte 			b[]=new byte[352];
			ByteBuffer		bb=ByteBuffer.wrap(b);
			
			dis.readFully(b,0,352);

			bb.order(java.nio.ByteOrder.LITTLE_ENDIAN);
			//System.out.println("sizeof_hdr: "+bb.getInt());
			dim[0]=bb.getShort(40);
			dim[1]=bb.getShort(42);
			dim[2]=bb.getShort(44);
			dim[3]=bb.getShort(46);
			//System.out.println("dim: "+dim[0]+","+dim[1]+","+dim[2]+","+dim[3]);
			datatype=bb.getShort(70);
			//System.out.println("datatype: "+datatype);
			pixdim[0]=bb.getFloat(76);
			pixdim[1]=bb.getFloat(80);
			pixdim[2]=bb.getFloat(84);
			pixdim[3]=bb.getFloat(88);
			//System.out.println("pixdim: "+pixdim[0]+","+pixdim[1]+","+pixdim[2]+","+pixdim[3]);
			
			byte ext[]=new byte[4];
			ext[0]=bb.get(348);
			ext[1]=bb.get(349);
			ext[2]=bb.get(350);
			ext[3]=bb.get(351);
			//System.out.println("ext:"+ext[0]+","+ext[1]+","+ext[2]+","+ext[3]);
			if(ext[0]==1)
			{
				int	extSize;
				dis.readFully(b,0,8);
				extSize=bb.getInt(0);
				//System.out.println("extsize:"+extSize);
				dis.skip(extSize-8);
			}
			
			volume=new byte[dim[1]*dim[2]*dim[3]*bytesPerVoxel()];
			dis.readFully(volume,0,volume.length);
			
			dis.close();
			
			// Get bounding box
			boundingBox[0]=dim[1];	// min i
			boundingBox[1]=0;		// max i
			boundingBox[2]=dim[2];	// min j
			boundingBox[3]=0;		// max j
			boundingBox[4]=dim[3];	// min k
			boundingBox[5]=0;		// max k
			float	val;
			for(int i=0;i<dim[1];i+=5)		// there's no need to scan all voxels...
			for(int j=0;j<dim[2];j+=5)
			for(int k=0;k<dim[3];k+=5)
			{
				val=getValue(i,j,k);
				if(val>0)
				{
					if(i<boundingBox[0])	boundingBox[0]=i;
					if(i>boundingBox[1])	boundingBox[1]=i;
					if(j<boundingBox[2])	boundingBox[2]=j;
					if(j>boundingBox[3])	boundingBox[3]=j;
					if(k<boundingBox[4])	boundingBox[4]=k;
					if(k>boundingBox[5])	boundingBox[5]=k;
				}
			}
		}
		catch(IOException e)
		{
			err=1;
		}
		
		return err;
	}
	public float getValue(int i)
	{
		// get value at voxel with absolute index i
		ByteBuffer		bb=ByteBuffer.wrap(volume);
		bb.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		float	v=0;
		switch(datatype)
		{
			case 2://DT_UINT8
				v=bb.get(i);
				break;
			case 4://DT_INT16
				v=bb.getShort(2*i);
				break;
			case 8://DT_INT32
				v=bb.getInt(4*i);
				break;
			case 16://DT_FLOAT32
				v=bb.getFloat(4*i);
				break;
		}
		return v;
	}
	public float getValue(int i, int j, int k)
	// get value at voxel with index coordinates i,j,k
	{
		ByteBuffer		bb=ByteBuffer.wrap(volume);
		bb.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		float	v=0;
		if(i>=dim[1]||j>=dim[2]||k>=dim[3])
			return v;
		switch(datatype)
		{
			case 2://DT_UINT8
				v=bb.get(k*dim[2]*dim[1]+j*dim[1]+i);
				break;
			case 4://DT_INT16
				v=bb.getShort(2*(k*dim[2]*dim[1]+j*dim[1]+i));
				break;
			case 8://DT_INT32
				v=bb.getInt(4*(k*dim[2]*dim[1]+j*dim[1]+i));
				break;
			case 16://DT_FLOAT32
				v=bb.getFloat(4*(k*dim[2]*dim[1]+j*dim[1]+i));
				break;
		}
		return v;
	}
    public MyVolume(String filename)
    {
    	volume=null;
    	int	err;
    	err=loadNiftiVolume(filename);
    }
}
class MyImage
{           
	int				initialized;
	QCApp			myQCApp;
	String			subject;
	String			imgList[];
	BufferedImage	img[];										// bitmap image
	int				cmap[]=new int[59*3];						// colour map for FIRST segmented image
	float			X[]={0,1,0,0, 0,0,1,0, 1,0,0,0, 0,0,0,1};	// X-plane transformation matrix
	float			Y[]={1,0,0,0, 0,0,1,0, 0,1,0,0, 0,0,0,1};	// Y-plane transformation matrix
	float			Z[]={1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};	// Z-plane transformation matrix
	
	public void multMatVec(float rV[], float M[], float V[])
	{
		rV[0]=M[0]*V[0]+M[1]*V[1]+M[2]*V[2]+M[3];
		rV[1]=M[4]*V[0]+M[5]*V[1]+M[6]*V[2]+M[7];
		rV[2]=M[8]*V[0]+M[9]*V[1]+M[10]*V[2]+M[11];
	}
	public float detMat(float M[])
	{
		return	M[1]*M[11]*M[14]*M[4]-M[1]*M[10]*M[15]*M[4]-M[11]*M[13]*M[2]*M[4]+
				M[10]*M[13]*M[3]*M[4]-M[0]*M[11]*M[14]*M[5]+M[0]*M[10]*M[15]*M[5]+
				M[11]*M[12]*M[2]*M[5]-M[10]*M[12]*M[3]*M[5]-M[1]*M[11]*M[12]*M[6]+
				M[0]*M[11]*M[13]*M[6]+M[1]*M[10]*M[12]*M[7]-M[0]*M[10]*M[13]*M[7]-
				M[15]*M[2]*M[5]*M[8]+M[14]*M[3]*M[5]*M[8]+M[1]*M[15]*M[6]*M[8]-
				M[13]*M[3]*M[6]*M[8]-M[1]*M[14]*M[7]*M[8]+M[13]*M[2]*M[7]*M[8]+
				M[15]*M[2]*M[4]*M[9]-M[14]*M[3]*M[4]*M[9]-M[0]*M[15]*M[6]*M[9]+
				M[12]*M[3]*M[6]*M[9]+M[0]*M[14]*M[7]*M[9]-M[12]*M[2]*M[7]*M[9];
	}
	public void invMat(float rM[], float M[])
	{
		float	d=detMat(M);
		int		i;
	
		rM[0] = -(M[11]*M[14]*M[5] - M[10]*M[15]*M[5] - M[11]*M[13]*M[6] + M[10]*M[13]*M[7] + M[15]*M[6]*M[9] - M[14]*M[7]*M[9]);
		rM[1] = M[1]*M[11]*M[14] - M[1]*M[10]*M[15] - M[11]*M[13]*M[2] + M[10]*M[13]*M[3] + M[15]*M[2]*M[9] - M[14]*M[3]*M[9];
		rM[2] = -(M[15]*M[2]*M[5] - M[14]*M[3]*M[5] - M[1]*M[15]*M[6] + M[13]*M[3]*M[6] + M[1]*M[14]*M[7] - M[13]*M[2]*M[7]);
		rM[3] = M[11]*M[2]*M[5] - M[10]*M[3]*M[5] - M[1]*M[11]*M[6] + M[1]*M[10]*M[7] + M[3]*M[6]*M[9] - M[2]*M[7]*M[9];
		
		rM[4] = M[11]*M[14]*M[4] - M[10]*M[15]*M[4] - M[11]*M[12]*M[6] + M[10]*M[12]*M[7] + M[15]*M[6]*M[8] - M[14]*M[7]*M[8];
		rM[5] = -(M[0]*M[11]*M[14] - M[0]*M[10]*M[15] - M[11]*M[12]*M[2] + M[10]*M[12]*M[3] + M[15]*M[2]*M[8] - M[14]*M[3]*M[8]);
		rM[6] = M[15]*M[2]*M[4] - M[14]*M[3]*M[4] - M[0]*M[15]*M[6] + M[12]*M[3]*M[6] + M[0]*M[14]*M[7] - M[12]*M[2]*M[7];
		rM[7] = -(M[11]*M[2]*M[4] - M[10]*M[3]*M[4] - M[0]*M[11]*M[6] + M[0]*M[10]*M[7] + M[3]*M[6]*M[8] - M[2]*M[7]*M[8]);
		
		rM[8] = -(M[11]*M[13]*M[4] - M[11]*M[12]*M[5] + M[15]*M[5]*M[8] - M[13]*M[7]*M[8] - M[15]*M[4]*M[9] + M[12]*M[7]*M[9]);
		rM[9] = -(M[1]*M[11]*M[12] - M[0]*M[11]*M[13] - M[1]*M[15]*M[8] + M[13]*M[3]*M[8] + M[0]*M[15]*M[9] - M[12]*M[3]*M[9]);
		rM[10]= -(M[1]*M[15]*M[4] - M[13]*M[3]*M[4] - M[0]*M[15]*M[5] + M[12]*M[3]*M[5] - M[1]*M[12]*M[7] + M[0]*M[13]*M[7]);
		rM[11]= M[1]*M[11]*M[4] - M[0]*M[11]*M[5] + M[3]*M[5]*M[8] - M[1]*M[7]*M[8] - M[3]*M[4]*M[9] + M[0]*M[7]*M[9];
		
		rM[12]= M[10]*M[13]*M[4] - M[10]*M[12]*M[5] + M[14]*M[5]*M[8] - M[13]*M[6]*M[8] - M[14]*M[4]*M[9] + M[12]*M[6]*M[9];
		rM[13]= M[1]*M[10]*M[12] - M[0]*M[10]*M[13] - M[1]*M[14]*M[8] + M[13]*M[2]*M[8] + M[0]*M[14]*M[9] - M[12]*M[2]*M[9];
		rM[14]= M[1]*M[14]*M[4] - M[13]*M[2]*M[4] - M[0]*M[14]*M[5] + M[12]*M[2]*M[5] - M[1]*M[12]*M[6] + M[0]*M[13]*M[6];
		rM[15]= -(M[1]*M[10]*M[4] - M[0]*M[10]*M[5] + M[2]*M[5]*M[8] - M[1]*M[6]*M[8] - M[2]*M[4]*M[9] + M[0]*M[6]*M[9]);
		
		for(i=0;i<16;i++)
			rM[i]*=1/d;
	}
	public void printStatusMessage(String msg)
	{
		if(myQCApp.status!=null)
		{
			myQCApp.status.setText(msg);
			myQCApp.status.paintImmediately(myQCApp.status.getVisibleRect());
		}
		else
			System.out.println(msg);
	}
	public int value2rgb(int v, int cmapindex)
	{
		int	rgb=0;
		int	r,g,b;
		
		switch(cmapindex)
		{
			case 1: // greyscale
				rgb=v<<16|v<<8|v;
				break;
			case 2:	// random label colours
				r=cmap[3*v+0];
				g=cmap[3*v+1];
				b=cmap[3*v+2];			
				rgb=r<<16|g<<8|b;
				break;
		}
		return rgb;
	}
	BufferedImage drawSlice(MyVolume vol, double t, int plane, int cmapindex)
	// draw slice with index 's' in the plane 'plane' at position ox, oy using colourmap 'cmapindex'
	{
		int		x,y,z;
		float	P[],invP[]=new float[16];	// view plane transformation matrix and its inverse
		float	tmp[]=new float[3],tmpd[]=new float[3];
		int		dim1[]=new int[3];
		int		s;
		
		// transform volume to view plane
		P=X;
		switch(plane)
		{
			case 1: P=X; break;
			case 2: P=Y; break;
			case 3: P=Z; break;
		}
		invMat(invP,P);
		tmp[0]=vol.dim[1];
		tmp[1]=vol.dim[2];
		tmp[2]=vol.dim[3];
		multMatVec(tmpd,P,tmp);
		dim1[0]=(int)tmpd[0];
		dim1[1]=(int)tmpd[1];
		dim1[2]=(int)tmpd[2];
		
		s=(int)(t*dim1[2]);
		if(s<0) s=0;
		if(s>=dim1[2]) s=dim1[2]-1;
		return drawSlice(vol,s,plane,cmapindex);
	}

	BufferedImage drawSlice(MyVolume vol, int s, int plane, int cmapindex)
	// draw slice with index 's' in the plane 'plane' at position ox, oy using colourmap 'cmapindex'
	{
		BufferedImage	theImg;
		int				x,y,z;
		int				x1,y1,z1;
		int				rgb;
		int				v;
		float			sliceMax=0;					// maximum slice value
		float			P[],invP[]=new float[16];	// view plane transformation matrix and its inverse
		float			tmp[]=new float[3],tmpd[]=new float[3],tmpx[]=new float[3];
		int				dim1[]=new int[3];
		float			pixdim1[]=new float[3];
		Rectangle		rect=new Rectangle(0,0,1,1);
		
		// transform volume to view plane
		P=X;
		switch(plane)
		{
			case 1: P=X; break;
			case 2: P=Y; break;
			case 3: P=Z; break;
		}
		invMat(invP,P);
		tmp[0]=vol.dim[1];
		tmp[1]=vol.dim[2];
		tmp[2]=vol.dim[3];
		multMatVec(tmpd,P,tmp);
		dim1[0]=(int)tmpd[0];
		dim1[1]=(int)tmpd[1];
		dim1[2]=(int)tmpd[2];

        tmp[0]=vol.pixdim[1];
        tmp[1]=vol.pixdim[2];
        tmp[2]=vol.pixdim[3];
        multMatVec(tmpd,P,tmp);
        pixdim1[0]=tmpd[0];
        pixdim1[1]=tmpd[1];
        pixdim1[2]=tmpd[2];

		// find bounding box
		tmp[0]=(float)vol.boundingBox[0];
		tmp[1]=(float)vol.boundingBox[2];
		tmp[2]=(float)vol.boundingBox[4];
		multMatVec(tmpd,P,tmp);
		rect.x=Math.max((int)tmpd[0]-10,0);
		rect.y=Math.max((int)tmpd[1]-10,0);
		tmp[0]=(float)vol.boundingBox[1];
		tmp[1]=(float)vol.boundingBox[3];
		tmp[2]=(float)vol.boundingBox[5];
		multMatVec(tmpd,P,tmp);
		rect.width=Math.min((int)tmpd[0]-rect.x+1+10,dim1[0]);
		rect.height=Math.min((int)tmpd[1]-rect.y+1+10,dim1[1]);
		
		// find maximum brightness
		z=s;
		for(x=rect.x;x<rect.width+rect.x;x++)
		for(y=rect.y;y<rect.height+rect.y;y++)
		{
			tmp[0]=x;
			tmp[1]=y;
			tmp[2]=z;
			multMatVec(tmpx,invP,tmp);
			x1=(int)tmpx[0];
			y1=(int)tmpx[1];
			z1=(int)tmpx[2];
			
			v=(int)vol.getValue(x1,y1,z1);
			if(v>sliceMax)
				sliceMax=v;
		}
		
		// draw slice
		theImg=new BufferedImage(rect.width,rect.height,BufferedImage.TYPE_INT_RGB);
		z=s;
		for(x=0;x<rect.width;x++)
		for(y=0;y<rect.height;y++)
		{
			tmp[0]=x+rect.x;
			tmp[1]=y+rect.y;
			tmp[2]=z;
			multMatVec(tmpx,invP,tmp);
			x1=(int)tmpx[0];
			y1=(int)tmpx[1];
			z1=(int)tmpx[2];
			
			v=(int)(vol.getValue(x1,y1,z1));
			if(cmapindex==1)
				rgb=value2rgb((int)(v*255.0/sliceMax),cmapindex);
			else
				rgb=value2rgb(v,cmapindex);
			if(v>0)
				theImg.setRGB(x,rect.height-1-y,rgb);
		}
        // scale
        BufferedImage	scaledImg=new BufferedImage((int)(rect.width*pixdim1[0]+0.5),(int)(rect.height*pixdim1[1]+0.5),BufferedImage.TYPE_INT_RGB);
        AffineTransform at=new AffineTransform();
        at.scale(pixdim1[0],pixdim1[1]);
        AffineTransformOp scaleOp=new AffineTransformOp(at,AffineTransformOp.TYPE_BILINEAR);
        return scaleOp.filter(theImg,scaledImg);
	}
	BufferedImage drawSlice(MyVolume vol, MyVolume volBack, double t, int plane, int cmapindex)
	// draw slice with index 's' in the plane 'plane' at position ox, oy using colourmap 'cmapindex'
	{
		int		x,y,z;
		float	P[],invP[]=new float[16];	// view plane transformation matrix and its inverse
		float	tmp[]=new float[3],tmpd[]=new float[3];
		int		dim1[]=new int[3];
		int		s;
		
		// transform volume to view plane
		P=X;
		switch(plane)
		{
			case 1: P=X; break;
			case 2: P=Y; break;
			case 3: P=Z; break;
		}
		invMat(invP,P);
		tmp[0]=vol.dim[1];
		tmp[1]=vol.dim[2];
		tmp[2]=vol.dim[3];
		multMatVec(tmpd,P,tmp);
		dim1[0]=(int)tmpd[0];
		dim1[1]=(int)tmpd[1];
		dim1[2]=(int)tmpd[2];
		
		s=(int)(t*dim1[2]);
		if(s<0) s=0;
		if(s>=dim1[2]) s=dim1[2]-1;
		return drawSlice(vol,volBack,s,plane,cmapindex);
	}

	BufferedImage drawSlice(MyVolume vol, MyVolume volBack, int s, int plane, int cmapindex)
	// draw slice with index 's' in the plane 'plane' at position ox, oy using colourmap 'cmapindex'
	{
		BufferedImage	theImg;
		int				x,y,z;
		int				x1,y1,z1;
		int				rgb,rgb0;
		int				v,v0;
		float			sliceMax=0;					// maximum slice value
		float			P[],invP[]=new float[16];	// view plane transformation matrix and its inverse
		float			tmp[]=new float[3],tmpd[]=new float[3],tmpx[]=new float[3];
		int				dim1[]=new int[3];
        float           pixdim1[]=new float[3];
		Rectangle		rect=new Rectangle(0,0,1,1);
		
		// transform volume to view plane
		P=X;
		switch(plane)
		{
			case 1: P=X; break;
			case 2: P=Y; break;
			case 3: P=Z; break;
		}
		invMat(invP,P);
		tmp[0]=volBack.dim[1];
		tmp[1]=volBack.dim[2];
		tmp[2]=volBack.dim[3];
		multMatVec(tmpd,P,tmp);
		dim1[0]=(int)tmpd[0];
		dim1[1]=(int)tmpd[1];
		dim1[2]=(int)tmpd[2];

        tmp[0]=vol.pixdim[1];
        tmp[1]=vol.pixdim[2];
        tmp[2]=vol.pixdim[3];
        multMatVec(tmpd,P,tmp);
        pixdim1[0]=tmpd[0];
        pixdim1[1]=tmpd[1];
        pixdim1[2]=tmpd[2];

		// find bounding box
		tmp[0]=(float)volBack.boundingBox[0];
		tmp[1]=(float)volBack.boundingBox[2];
		tmp[2]=(float)volBack.boundingBox[4];
		multMatVec(tmpd,P,tmp);
		rect.x=Math.max((int)tmpd[0]-10,0);
		rect.y=Math.max((int)tmpd[1]-10,0);
		tmp[0]=(float)volBack.boundingBox[1];
		tmp[1]=(float)volBack.boundingBox[3];
		tmp[2]=(float)volBack.boundingBox[5];
		multMatVec(tmpd,P,tmp);
		rect.width=Math.min((int)tmpd[0]-rect.x+1+10,dim1[0]);
		rect.height=Math.min((int)tmpd[1]-rect.y+1+10,dim1[1]);
		
		// find maximum brightness
		z=s;
		for(x=rect.x;x<rect.width+rect.x;x++)
		for(y=rect.y;y<rect.height+rect.y;y++)
		{
			tmp[0]=x;
			tmp[1]=y;
			tmp[2]=z;
			multMatVec(tmpx,invP,tmp);
			x1=(int)tmpx[0];
			y1=(int)tmpx[1];
			z1=(int)tmpx[2];
			
			v=(int)volBack.getValue(x1,y1,z1);
			if(v>sliceMax)
				sliceMax=v;
		}
		
		// draw slice
		theImg=new BufferedImage(rect.width,rect.height,BufferedImage.TYPE_INT_RGB);
		z=s;
		for(x=0;x<rect.width;x++)
		for(y=0;y<rect.height;y++)
		{
			tmp[0]=x+rect.x;
			tmp[1]=y+rect.y;
			tmp[2]=z;
			multMatVec(tmpx,invP,tmp);
			x1=(int)tmpx[0];
			y1=(int)tmpx[1];
			z1=(int)tmpx[2];
			
			v=(int)(vol.getValue(x1,y1,z1));
			v0=(int)(volBack.getValue(x1,y1,z1));
			rgb=value2rgb(v,cmapindex);
			rgb0=value2rgb((int)(v0*255.0/sliceMax),1);
			if(v>0)
				theImg.setRGB(x,rect.height-1-y,rgb);
			else
				theImg.setRGB(x,rect.height-1-y,rgb0);
		}
        // scale
        BufferedImage	scaledImg=new BufferedImage((int)(rect.width*pixdim1[0]+0.5),(int)(rect.height*pixdim1[1]+0.5),BufferedImage.TYPE_INT_RGB);
        AffineTransform at=new AffineTransform();
        at.scale(pixdim1[0],pixdim1[1]);
        AffineTransformOp scaleOp=new AffineTransformOp(at,AffineTransformOp.TYPE_BILINEAR);
        return scaleOp.filter(theImg,scaledImg);
	}
	BufferedImage drawVolume(MyVolume vol, int plane, int cmapindex)
	{
		BufferedImage	theImg;
		int		x,y,z,x1,y1,z1,rgb;
		float	s0,s1;
		int		v;
		float	sliceMax=0;
		float	P[],invP[]=new float[16],tmp[]=new float[3],tmpd[]=new float[3],tmpx[]=new float[3];
		int		dim1[]=new int[3];
        float   pixdim1[]=new float[3];
		int		r,g,b;
		Rectangle	rect=new Rectangle(0,0,0,0);
		
		// transform volume to view plane
		P=X;
		switch(plane)
		{
			case 1: P=X; break;
			case 2: P=Y; break;
			case 3: P=Z; break;
		}
		invMat(invP,P);
		tmp[0]=vol.dim[1];
		tmp[1]=vol.dim[2];
		tmp[2]=vol.dim[3];
		multMatVec(tmpd,P,tmp);
		dim1[0]=(int)tmpd[0];
		dim1[1]=(int)tmpd[1];
		dim1[2]=(int)tmpd[2];

        tmp[0]=vol.pixdim[1];
        tmp[1]=vol.pixdim[2];
        tmp[2]=vol.pixdim[3];
        multMatVec(tmpd,P,tmp);
        pixdim1[0]=tmpd[0];
        pixdim1[1]=tmpd[1];
        pixdim1[2]=tmpd[2];

		// find 1st and last non-empty slices (for lighting) and bounding box
		s0=dim1[2]; // 1st
		s1=0;		// last
		rect.x=dim1[0];
		rect.y=dim1[1];
		for(z=0;z<dim1[2];z++)
		for(x=0;x<dim1[0];x++)
		for(y=0;y<dim1[1];y++)
		{
			tmp[0]=x;
			tmp[1]=y;
			tmp[2]=z;
			multMatVec(tmpx,invP,tmp);
			x1=(int)tmpx[0];
			y1=(int)tmpx[1];
			z1=(int)tmpx[2];
			
			v=(int)vol.getValue(x1,y1,z1);

			if(v>0)
			{
				if(z<s0)	s0=z;
				if(z>s1)	s1=z;

				if(x<rect.x)		rect.x=x;
				if(y<rect.y)		rect.y=y;
				if(x>rect.width)	rect.width=x;
				if(y>rect.height)	rect.height=y;
			}
		}
		rect.x=Math.max(rect.x-10,0);
		rect.y=Math.max(rect.y-10,0);
		rect.width=Math.min(rect.width-rect.x+1+10,dim1[0]);
		rect.height=Math.min(rect.height-rect.y+1+10,dim1[1]);

		// draw volume
		theImg=new BufferedImage(rect.width,rect.height,BufferedImage.TYPE_INT_RGB);
		for(z=0;z<dim1[2];z++)
		for(x=0;x<rect.width;x++)
		for(y=0;y<rect.height;y++)
		{
			tmp[0]=x+rect.x;
			tmp[1]=y+rect.y;
			tmp[2]=z;
			multMatVec(tmpx,invP,tmp);
			x1=(int)tmpx[0];
			y1=(int)tmpx[1];
			z1=(int)tmpx[2];
			
			v=(int)vol.getValue(x1,y1,z1);
			if(v==0)
				continue;
			rgb=value2rgb(v,cmapindex); //if(rgb==0) System.out.println("missing label:"+v);
			
			// light
			r=(int)((rgb>>16)        *Math.pow((z-s0)/(s1-s0),0.5));
			g=(int)(((rgb&0xff00)>>8)*Math.pow((z-s0)/(s1-s0),0.5));
			b=(int)((rgb&0xff)       *Math.pow((z-s0)/(s1-s0),0.5));
			rgb=r<<16|g<<8|b;

			theImg.setRGB(x,rect.height-1-y,rgb);
		}
        // scale
        BufferedImage	scaledImg=new BufferedImage((int)(rect.width*pixdim1[0]+0.5),(int)(rect.height*pixdim1[1]+0.5),BufferedImage.TYPE_INT_RGB);
        AffineTransform at=new AffineTransform();
        at.scale(pixdim1[0],pixdim1[1]);
        AffineTransformOp scaleOp=new AffineTransformOp(at,AffineTransformOp.TYPE_BILINEAR);
        return scaleOp.filter(theImg,scaledImg);
	}
	BufferedImage drawErrorSlice()
	{
		BufferedImage	theImg=new BufferedImage(128,128,BufferedImage.TYPE_INT_RGB);
		Graphics2D 		g2=theImg.createGraphics();
		
		g2.setFont(new Font("Helvetica", Font.BOLD, 14));
		g2.drawString("UNAVAILABLE",15,64);
		g2.drawRect(1,1,126,126);
		
		return theImg;
	}
    String getVolumeName(String name)
    {
    	int	i;
    	i=name.indexOf(".");
    	return name.substring(0,i);
    }
    String getPlaneName(String name)
    {
    	int	i0,i1;
    	i0=name.indexOf(".");
    	i1=name.lastIndexOf(".");
    	return name.substring(i0+1,i1);
    }
    String getImageTypeName(String name)
    {
    	int	i;
    	i=name.lastIndexOf(".");
    	return name.substring(i+1,name.length());
    }
	public int setVolume(java.lang.String filename)
	{
		File		f;
		int			i;
		String		volName="",tmp;
		MyVolume	vol=null;
		int			err=0;
		
		subject=filename;
		
		img=new BufferedImage[imgList.length];
		for(i=0;i<imgList.length;i++)
		{
			String	name=subject+"/qc/"+imgList[i]+".png";
			f=new File(name);
			if(f.exists())
			{
				// QC images available: load them
				printStatusMessage("Loading image \""+name+"\"...");
				try{img[i] = ImageIO.read(f);}
				catch (IOException e){}
			}
			else
			{
				// QC images unavailable: make them (and save them)
				tmp=getVolumeName(imgList[i]);
				if(!volName.equals(tmp))
				{
					volName=tmp;
					printStatusMessage("Loading volume \""+volName+"\"...");
					vol=new MyVolume(subject+"/"+volName+".nii.gz");
				}
				if(vol.volume==null)
				{
					printStatusMessage("ERROR: Volume \""+subject+"/"+volName+".nii.gz\" unavailable.");
					img[i]=drawErrorSlice();
					err=1;
				}
				else
				{
					String		volPlane=getPlaneName(imgList[i]);
					String		imgType=getImageTypeName(imgList[i]);
					int			cmapindex;
					int			plane;
	
					printStatusMessage("Drawing volume \""+volName+"\", plane:"+volPlane+"...");
					
					plane=1;
					if(volPlane.equals("X")) plane=1;
					if(volPlane.equals("Y")) plane=2;
					if(volPlane.equals("Z")) plane=3;
					
					cmapindex=1;
					if(volName.equals("first12_all_fast_firstseg"))
						cmapindex=2;
					
					if(imgType.equals("2D"))
						img[i]=drawSlice(vol,0.5,plane,cmapindex);
					else
						img[i]=drawVolume(vol,plane,cmapindex);
					
					// save image (create directory qc if it does not exist)
					File qcdir=new File(subject+"/qc");
					if(!qcdir.exists())
						qcdir.mkdir();
					try{ImageIO.write(img[i],"png",f);}catch(IOException e){}
				}
			}
		}
		initialized=1;
		
		return err;
	}
    public MyImage()
    {
 		int i;
 		
 		initialized=0;
 		
		// init segmentation label colourmap		
		int	tmp[]={	1,255,0,0,		// LFg
					2,255,34,0,		// LPg
					3,255,67,0,		// LOg
					4,255,101,0,	// LTg
					5,255,134,0,	// LSubg
					11,255,168,0,	// RFg
					12,255,201,0,	// RPg
					13,255,235,0,	// ROg
					14,228,255,0,	// RTg
					15,161,255,0,	// RSubg
					1,94,255,0,		// LFw
					2,27,255,0,		// LPw
					3,0,215,40,		// LOw
					4,0,148,107,	// LTw
					5,0,81,174,		// LSubw
					11,0,13,242,	// RFw
					12,36,0,255,	// RPw
					13,81,0,255,	// ROw
					14,125,0,255,	// RTw
					15,170,0,255};	// RSubw

		for(i=0;i<20;i++)
		{
			cmap[3*tmp[i*4]+0]=tmp[i*4+1];
			cmap[3*tmp[i*4]+1]=tmp[i*4+2];
			cmap[3*tmp[i*4]+2]=tmp[i*4+3];
		}
		
		// init image list
		String tmpList[]={	"bet.X.2D","bet.Y.2D","bet.Z.2D",
							"segmented_seg.X.2D","segmented_seg.Y.2D","segmented_seg.Z.2D",
							"sub2mni.X.2D","sub2mni.Y.2D","sub2mni.Z.2D"};
		imgList=new String[tmpList.length];
		for(i=0;i<tmpList.length;i++)
			imgList[i]=tmpList[i];
    }
}
class MyImages extends JComponent
{           
	QCApp		myQCApp;
	MyImage		image;
	Rectangle	rect[];
	int			selectedImage;
	double		selectedSlice;
	MyVolume	vol;
	MyVolume	volBack;
	String		volName;
	String		subName;

    public void paint(Graphics g)
    {
    	Dimension dim=this.getSize();
    	g.setColor(Color.black);
    	g.fillRect(0,0,dim.width,dim.height);
    	
		if(image.initialized==0)
			return;
		
		if(rect==null)
			rect=new Rectangle[image.img.length];
		
		if(selectedImage==0)
		{
			// All images view
			int		i;
			int		xoff=0,yoff=0,maxHeight;
			int		x0,y0,x1,y1;
			double	z=2;
			
			maxHeight=0;
			for(i=0;i<image.img.length;i++)
			{
				if(xoff+z*image.img[i].getWidth()>=this.getParent().getSize().width)
				{
					xoff=0;
					yoff+=maxHeight;
					maxHeight=0;
				}
				g.drawImage(image.img[i],xoff,yoff,(int)(z*image.img[i].getWidth()),(int)(z*image.img[i].getHeight()),null);
				rect[i]=new Rectangle(xoff,yoff,(int)(z*image.img[i].getWidth()),(int)(z*image.img[i].getHeight()));
				xoff+=(int)(z*image.img[i].getWidth());
				if(z*image.img[i].getHeight()>maxHeight)
					maxHeight=(int)(z*image.img[i].getHeight());
			}
			
			// adjust image size for scroll
			Dimension d=new Dimension(this.getParent().getSize().width,yoff+maxHeight);
			if(!d.equals(this.getParent().getSize()))
			{
				this.setPreferredSize(d);
				this.revalidate();
			}
		}
		else
		{
			// Single volume view			
			int				i=selectedImage-1;
			BufferedImage	img;
			String			tmp;
			String			filename;

			// load volume
			tmp=image.getVolumeName(image.imgList[i]);
			if(volName==null || !volName.equals(tmp) || !subName.equals(image.subject))
			{
				volName=tmp;
				subName=image.subject;
				image.printStatusMessage("Loading volume \""+volName+"\"...");
				filename=image.subject+"/"+volName+".nii.gz";
				vol=new MyVolume(filename);
				image.printStatusMessage("Volume: "+filename);
				if(volName.equals("brain"))
					volBack=vol;
			}
			String		volPlane=image.getPlaneName(image.imgList[i]);
			int			cmapindex;
			int			plane;
			plane=1;
			if(volPlane.equals("X")) plane=1;
			if(volPlane.equals("Y")) plane=2;
			if(volPlane.equals("Z")) plane=3;
			cmapindex=1;
			img=image.drawSlice(vol,selectedSlice,plane,cmapindex);
			
			// adjust image size for scroll
			Dimension	d=this.getParent().getSize();
			if(!d.equals(this.getSize()))
			{
				this.setPreferredSize(d);
				this.revalidate();
				return;
			}

			// draw image
			double		scale=this.getSize().height/(double)img.getHeight();
			int			xoff,yoff;
			
			xoff=(int)((this.getSize().width-img.getWidth()*scale)/2.0);
			yoff=0;
			g.drawImage(img,xoff,yoff,(int)(scale*img.getWidth()),(int)(scale*img.getHeight()),null);
			g.setColor(Color.white);
			g.drawRoundRect(d.width-10-48,10,48,20,15,15);
			String	back="BACK";
			FontMetrics fm = this.getFontMetrics(this.getFont());
			int width = fm.stringWidth(back);
			int height = fm.getHeight();
			g.drawString("BACK",d.width-10-48 +(48-width)/2,10+20-(20-height)/2-3);
		}
    }
    public MyImages()
    {
    	selectedImage=0;
    	selectedSlice=0.5;
    	vol=null;
    	volBack=null;
    	volName=null;
    	subName=null;
    	//myQCApp=this.getParent();
    	image=new MyImage();
    	//image.myQCApp=myQCApp;
    }
}
class MyGraphs extends JComponent
{           
	QCApp	myQCApp;
	String	subjectsDir;
    int		nVolumes=23;
	String	regions[]={"ICV+","ICV","BV","LFg","LPg","LOg","LTg","LSubg","RFg","RPg","ROg","RTg","RSubg","LFw","LPw","LOw","LTw","LSubw","RFw","RPw","ROw","RTw","RSubw"};
	double	mean[]=new double[23];
	double	std[]=new double[23];
    double	R=50;
    double	selectedSubjectVolumes[]=new double[23];
	
    public void paint(Graphics g)
	{
    	Graphics2D	g2=(Graphics2D)g;
    	float		val;
    	int			i,x[]=new int[nVolumes+1];
    	Dimension	dim=this.getSize();
    	Stroke		defaultStroke=g2.getStroke(),dashed=new BasicStroke(1.0f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,5.0f,new float[]{5.0f},0.0f);
    	
    	for(i=0;i<=nVolumes;i++)
    		x[i]=(int)((dim.width-1)*i/(double)nVolumes);
    	
    	// draw brain structure bars, with colours depending on selected-subject values
    	g2.setColor(Color.black);
    	for(i=0;i<nVolumes;i++)
    	{
    		if(selectedSubjectVolumes[0]!=0)
    		{
    			val=(float)((selectedSubjectVolumes[i]-mean[i])/(2.0*std[i]));
    			if(val>=0 && val<=1)	g2.setColor(new Color(val,1.0f-val,0.0f));
		    	else
		    	if(val>=-1 && val<0)	g2.setColor(new Color(0.0f,1.0f+val,-val));
		    	else
		    							g2.setColor(Color.white);
    		}
    		else
    			g2.setColor(Color.white);
			g2.fillRect(x[i],0,x[i+1],dim.height);
    		g2.setColor(Color.black);
    		g2.drawRect(x[i],0,x[i+1],dim.height);
    	}
    	
    	// draw dots for selected subject values
    	g2.setColor(Color.black);
    	if(selectedSubjectVolumes[0]!=0)
    	for (i=0;i<nVolumes;i++)
    	{
    		val=(float)(0.5f+(selectedSubjectVolumes[i]-mean[i])/(2.0*std[i])/2.0);
    		if(val<0) val=0;
    		if(val>1) val=1;
	    	g2.fillOval((x[i]+x[i+1])/2-5,(int)(dim.height*(1-val))-5,11,11);
    	}
    	
    	// draw mean and +/- 1 std values
    	g2.setColor(Color.black);
    	g2.drawLine(0,dim.height/2,dim.width,dim.height/2);
		g2.setStroke(dashed);
    	g2.drawLine(0,dim.height/4,dim.width,dim.height/4);
    	g2.drawLine(0,dim.height*3/4,dim.width,dim.height*3/4);

    	// draw brain structure names
    	for (i=0;i<nVolumes;i++)
    	{
			g2.translate((x[i]+x[i+1])/2,0);
			g2.rotate(Math.PI/2.0);
			g2.drawString(regions[i], 0, 0);
			g2.rotate(-Math.PI/2.0);
			g2.translate(-(x[i]+x[i+1])/2,0);
    	}
	}
	public int getVolumesForSubject(String filename, double x[])
	{
		BufferedReader	input;
		Scanner			sc;
		int				i;
		int				err=0;
		double			BV=0;

		for(i=0;i<nVolumes;i++)
			x[i]=0;
		
		// Load ICV and BrainSeg data
		try
		{
			input=new BufferedReader(new FileReader(subjectsDir+"/"+filename+"/volumes.txt"));
			//input.readLine(); // skip the first line
			sc=new Scanner(input);
			sc.nextLine();	// skip max jacobian
			sc.useDelimiter(Pattern.compile("[\r\n\\s]"));
			for(i=0;i<20;i++)
			{
				x[i+3]=sc.nextFloat();
				BV+=x[i+3];
			}
			x[2]=BV;
			sc.nextLine();
			sc.next();
			x[1]=1/sc.nextFloat();
			sc.next();
			x[0]=1/sc.nextFloat();
			sc.close();
		}
		catch (IOException e)
		{
			err=1;
			return err;
		}
		catch (InputMismatchException e)
		{
			err=1;
			return err;
		}
		catch (java.util.NoSuchElementException e)
		{
			err=1;
			return err;
		}
		
		return err;
	}
	public void configure(String dir)
	{
		subjectsDir=dir;
		double		s0=0;
		double		s1[]=new double[nVolumes];
		double		s2[]=new double[nVolumes];
		double		x[]=new double[nVolumes];
		int			i,j;
		File		files[]=(new File(dir)).listFiles();
		int			err;

		for(i=0;i<files.length;i++)
		{
			if(files[i].getName().charAt(0)=='.')
				continue;
			err=getVolumesForSubject(files[i].getName(),x);
			if(err==1)
			{
				myQCApp.setQC(files[i].getName(),2,"Segmentation results unavailable");
				continue;
			}
			
			for(j=0;j<nVolumes;j++)
			{
				s1[j]+=x[j];
				s2[j]+=x[j]*x[j];				
			}
			s0++;
			myQCApp.images.image.printStatusMessage((i+1)+"/"+files.length);
    	}
    	
    	System.out.println("N:"+s0);
    	for(j=0;j<nVolumes;j++)
    	{
			mean[j]=s1[j]/s0;
			std[j]=Math.sqrt((s0*s2[j]-s1[j]*s1[j])/(s0*(s0-1)));
			System.out.println(regions[j]+":\t"+mean[j]+" ± "+std[j]);
		}
	}
	public void setSelectedSubject(String filename)
	{
		getVolumesForSubject(filename,selectedSubjectVolumes);
		repaint();
	}
    public MyGraphs()
    {
    	//myQCApp=this.getParent();
    }
}
public class QCApp
{
	static JFrame 				f;
	static JTextArea			status;
	static JButton				chooseButton;
	static JButton				saveButton;
	static JTable 				table;
	static DefaultTableModel	model;
	static MyImages 			images;
	static MyGraphs 			graphs;
	static String				subjectsDir;
	static QCApp				me;
	
	public static void mouseDownOnImage(MouseEvent e)
	{
		int	i;
		
		if(images.selectedImage==0)
		{
			for(i=0;i<images.image.img.length;i++)
			{
				if(images.rect[i].contains(e.getPoint()))
				{
					images.selectedImage=i+1;
					break;
				}
			}
			images.repaint();
		}
		else
		{
			Dimension d=images.getParent().getSize();
			Rectangle backRect=new Rectangle(d.width-10-48,10,48,20);
			if(backRect.contains(e.getPoint()))
			{
				images.selectedImage=0;
				images.selectedSlice=0.5;
				images.repaint();
			}
		}
	}
	public static void mouseDraggedOnImage(MouseEvent e)
	{
		int	i;
		images.selectedSlice=e.getPoint().y/(double)images.getSize().height;
		images.repaint();
	}
	public static void selectSubject(JTable table)
	{
		int		i=table.getSelectedRows()[0];
		String	subject=model.getValueAt(i,2).toString();
		
		images.volBack=null;
		images.image.setVolume(subjectsDir+"/"+subject);
		images.repaint();
    	images.image.printStatusMessage("Subject: "+subject+".");
    	
    	graphs.setSelectedSubject(subject);
	}
	public static void chooseDirectory()
	{
		// select Subjects directory
		final JFileChooser fc=new JFileChooser();
		fc.setDialogTitle("Choose Subjects Directory...");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int returnVal=fc.showOpenDialog(null);
		if(returnVal!=JFileChooser.APPROVE_OPTION)
			return;
		File file = fc.getSelectedFile();
		subjectsDir=file.getAbsolutePath();
    	images.image.printStatusMessage("Subjects Directory: "+subjectsDir+".");
		
		// add files to table
		File			files[]=file.listFiles();
        Vector<Object>	row;
        int				n=1;
        int				i,j,k,l;
		for(i=0;i<files.length;i++)
		{
			if(files[i].getName().charAt(0)=='.' || files[i].isFile())
				continue;
			
			images.image.printStatusMessage((i+1)+"/"+files.length);
			
			row= new Vector<Object>();
			row.add(n);
			row.add(1);
			row.add(files[i].getName());
			row.add(new String());
			model.insertRow(model.getRowCount(),row);
			n++;
    	}
    	
    	// configure stats graphs
    	graphs.configure(subjectsDir);
    	
    	// if there is a qc.txt file inside subjectsDir, load it.
    	File			f=new File(subjectsDir+"/qc.txt");
    	if(f.exists())
    	{
    		System.out.println("qc.txt file present, read it.");
			BufferedReader	input;
    		StringTokenizer	st;
    		int				qc;
    		String			sub;
    		String			comment;
    		String			line;
			try
			{
				input=new BufferedReader(new FileReader(f));
				input.readLine(); // skip header row
				
				for(i=0;i<model.getRowCount();i++)
				{
					line=input.readLine();
					j=line.indexOf("\t");
					k=line.indexOf("\t",j+1);
					l=line.indexOf("\t",k+1);
					sub=line.substring(0,j);
					qc=Integer.parseInt(line.substring(j+1,k));
					comment=line.substring(k+1,l);
					if(!sub.equals(model.getValueAt(i,2).toString()))
					{
						System.out.println("ERROR: qc.txt file does not match current Subjects directory ["+sub+" vs. "+model.getValueAt(i,2).toString()+"]");
						images.image.printStatusMessage("ERROR: qc.txt file does not match current Subjects directory");
						return;
					}
					model.setValueAt(qc,i,1);
					model.setValueAt(comment,i,3);
				}
				input.close();
			}
			catch (IOException e){}
    	}
    	
    	images.image.printStatusMessage(model.getRowCount()+" subjects read.");
	}
	public void setQC(String subject,int QCValue, String msg)
	{
		int	i;
		for(i=0;i<model.getRowCount();i++)
			if(model.getValueAt(i,2).toString().equals(subject))
			{
				model.setValueAt(0,i,1);
				model.setValueAt(msg,i,3);
				break;
			}
	}
	public static void saveQC()
	{
		// Save QC
		final JFileChooser fc=new JFileChooser();
		fc.setDialogTitle("Save QC File...");
		int returnVal=fc.showSaveDialog(null);
		if(returnVal!=JFileChooser.APPROVE_OPTION)
			return;
			
		File	file = fc.getSelectedFile();
	    try
	    {
	    	int		i,j;
	    	double	x[]=new double[23];
	    	Writer	output = new BufferedWriter(new FileWriter(file));
	    	String	sub;
	    	
	    	output.write("Subject\tQC\tComments\t");
	    	for(j=0;j<23;j++)
	    		if(j<22)
	    			output.write(graphs.regions[j]+"\t");
	    		else
	    			output.write(graphs.regions[j]+"\n");
	    		
			for(i=0;i<model.getRowCount();i++)
			{
				sub=model.getValueAt(i,2).toString();
				output.write(sub+"\t");									// Subject
				output.write(model.getValueAt(i,1).toString()+"\t");	// QC
				output.write(model.getValueAt(i,3).toString()+"\t");	// Comments
				
				graphs.getVolumesForSubject(sub,x);						// Volumes
				for(j=0;j<23;j++)
					if(j<22)
						output.write(x[j]+"\t");
					else
						output.write(x[j]+"\n");
			}
			output.close();
		}
		catch (IOException e){}
	}
	public static void createAndShowGUI()
	{
		f = new JFrame("QCApp");
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){System.exit(0);}});

		// Status text
		status=new JTextArea("Choose a Subjects Directory");
		status.setOpaque(false);
		
		// Choose Button
		chooseButton=new JButton("Choose Subjects Directory...");
		chooseButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){chooseDirectory();}});
		
		// Save Button
		saveButton=new JButton("Save QC...");
		saveButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){saveQC();}});
		
		// Table
		model=new DefaultTableModel();
		table=new JTable(model);
		model.addColumn("#");
		model.addColumn("QC");
		model.addColumn("Subject");
		model.addColumn("Comments");
		table.setPreferredScrollableViewportSize(new Dimension(250,70));
		table.setFillsViewportHeight(true);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener(){public void valueChanged(ListSelectionEvent e){selectSubject(table);}});
		JScrollPane scrollPane=new JScrollPane(table);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		table.getColumnModel().getColumn(0).setMinWidth(32);
		table.getColumnModel().getColumn(1).setMinWidth(32);
		table.getColumnModel().getColumn(2).setPreferredWidth(800);
		table.getColumnModel().getColumn(3).setPreferredWidth(800);
		
		// Graphs
		graphs=new MyGraphs();
		graphs.setPreferredSize(new Dimension(250,250));
		graphs.myQCApp=me;

		// Image
		images=new MyImages();
		images.setPreferredSize(new Dimension(800,512));
		images.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){mouseDownOnImage(e);}});
		images.addMouseMotionListener(new MouseAdapter(){public void mouseDragged(MouseEvent e){mouseDraggedOnImage(e);}});
		images.myQCApp=me;
		images.image.myQCApp=me;
		JScrollPane imagesScrollPane=new JScrollPane(images);
		
		// Split Pane for Table and Graphs
		JSplitPane splitPaneForTableAndGraphs=new JSplitPane(JSplitPane.VERTICAL_SPLIT,scrollPane,graphs);
		splitPaneForTableAndGraphs.setOneTouchExpandable(true);
		splitPaneForTableAndGraphs.setDividerLocation(350);
		splitPaneForTableAndGraphs.setResizeWeight(1.0);

		// Split Pane for the previous and Images
		JSplitPane splitPane=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,splitPaneForTableAndGraphs,imagesScrollPane);
		splitPane.setOneTouchExpandable(true);
		splitPane.setDividerLocation(350);
		
		// Layout the GUI
	    GroupLayout layout = new GroupLayout(f.getContentPane());
        f.getContentPane().setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup
        (	layout.createParallelGroup()
        	.addGroup
        	(	layout.createSequentialGroup()
        		.addComponent(chooseButton)
        		.addComponent(saveButton)
        	)
        	.addComponent(splitPane)
        	.addComponent(status)
        );
        layout.setVerticalGroup
        (	layout.createSequentialGroup()
        	.addGroup
        	(	layout.createParallelGroup() //BASELINE
				.addComponent(chooseButton)
				.addComponent(saveButton)
        	)
        	.addComponent(splitPane)
	        .addComponent(status,GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,GroupLayout.PREFERRED_SIZE)
        );
		f.pack();
		f.setVisible(true);
    }
    public static void main(String[] args)
    {
    	if(args.length==1)
    	{
    		MyImage	tmp=new MyImage();
    		tmp.setVolume(args[0]);
    	}
    	else
    	{
	    	/*
	    	try
	    	{
	    		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
	    	}
	    	catch(ClassNotFoundException e){}
	    	catch(InstantiationException e){}
	    	catch(IllegalAccessException e){}
	    	catch(UnsupportedLookAndFeelException e){}
	    	
    		javax.swing.SwingUtilities.invokeLater
    		(
    			new Runnable()
    			{
    				public void run()
    				{
    					createAndShowGUI();
    				}
    			}
    		);
    		*/
    		me=new QCApp();
    		me.createAndShowGUI();
    	}
    }
}