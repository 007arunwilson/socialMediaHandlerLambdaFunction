package com.amazonaws.lambda.demo;

import java.awt.image.BufferedImage;
import org.imgscalr.Scalr;

public class ScalrImageProcessor {
	
    public static BufferedImage resizeImageProportional(BufferedImage bufferedImage, float width, float height) {	    	;
		//img.resize(width, height, true);

		//context.getLogger().log("Old " + String.valueOf(img.getWidth()) + "x" + String.valueOf(img.getHeight()) + "\n");
		//context.getLogger().log("Resize " + String.valueOf(width) + "x" + String.valueOf(height) + "\n");
		
		//find the ratio to resize
		double w_ratio = width/ bufferedImage.getWidth();
		double h_ratio = height / bufferedImage.getHeight();
		double ratio = Math.max(w_ratio, h_ratio);
		
		//context.getLogger().log("Ratio " + String.valueOf(w_ratio) + " - " + String.valueOf(h_ratio) + " - " + String.valueOf(ratio) + "\n");
		
		double new_w = bufferedImage.getWidth() * ratio;
		double new_h = bufferedImage.getHeight() * ratio;
		
		//context.getLogger().log("New " + String.valueOf(new_w) + "x" + String.valueOf(new_h) + "\n");

		bufferedImage = Scalr.resize(bufferedImage, Scalr.Method.ULTRA_QUALITY ,Scalr.Mode.AUTOMATIC, (int)new_w, (int)new_h);
		bufferedImage.flush();			
			
		return bufferedImage;
	}
    
    public static BufferedImage generateThumbnail(BufferedImage bufferedImage, float width_, float height_) {
    	
    	bufferedImage = resizeImageProportional(bufferedImage,width_,height_);
    	
		int width = (int)width_;
		int height = (int)height_;
		
		int final_x = 0;
		int final_y = 0;
		int final_w = (int)width;
		int final_h = (int)height;
		
		if(bufferedImage.getWidth() <= width) {
			final_w = bufferedImage.getWidth();
		}else {
			final_x = (bufferedImage.getWidth() - width) / 2;
		}
		
		if(bufferedImage.getHeight() <= height) {
			final_h = bufferedImage.getHeight();
		}else {
			final_y = (bufferedImage.getHeight() - height) / 2;
		}
		
    	//Cropping Image
		int x= final_x;
		int y= final_y;
		
		//check if the x and y less than zero
		if( x < 0){
			width = width + x;  //minus off the extra area
			x = 0;		//reset the X to zero
		}
		if( y < 0){
			height = height + y;  //minus off the extra area
			y = 0;		//reset the X to zero
		}
		
		//check if the crop width and height going out of bound
		width = (((x + width) > bufferedImage.getWidth()) ? (bufferedImage.getWidth() - x - 1) : width);
		height = (((y + height) > bufferedImage.getHeight()) ? (bufferedImage.getHeight() - y - 1) : height);
		
		if(width > 0 && height > 0) {
			bufferedImage = Scalr.crop(bufferedImage, x, y, width, height);
			bufferedImage.flush();
		}else {
		}
		
    	return bufferedImage;
	}
	
}

