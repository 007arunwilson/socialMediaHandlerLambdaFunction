package com.amazonaws.lambda.demo;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.amazonaws.lambda.demo.ScalrImageProcessor;

import javax.imageio.ImageIO;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;

public class LambdaFunctionHandler implements RequestHandler<Object, String> {
	
    public static Map<String, String> globalParamtersList = new HashMap<String, String>();
    public static Context context;
    
    @Override
    public String handleRequest(Object input, Context context_) {
    	
    	context = context_;
    	this.setGlobalParametersList(input);
    	
    	this.startProcess();

        // TODO: implement your handler
        return null;
    }
    
	private void startProcess() {
		
		context.getLogger().log(globalParamtersList.get("external_media_id"));
		
		String external_media_id = globalParamtersList.get("external_media_id").toString();
        Map<String, String> request_parameters = new HashMap<String, String>();
        request_parameters.put("type", "GET_DATA_OF_EXTERNAL_MEDIA");
        request_parameters.put("external_media_id",external_media_id);
        JSONObject responseJsonObject = (JSONObject) sendProgressCallback(request_parameters);
        
        JSONObject external_media_data = (JSONObject) responseJsonObject.get("external_media_data");
        JSONObject external_media_parent_media_data = (JSONObject) responseJsonObject.get("external_media_parent_data");
        
        String imageRemoteUrl = external_media_data.get("external_url").toString();
        
        Image remoteImage =  this.readImageFromRemoteUrl(imageRemoteUrl);
        BufferedImage bufferedImage = this.toBufferedImage(remoteImage);
        

		
		
		File lamdaFileStream = new File("/tmp/"+external_media_parent_media_data.get("file_value").toString());
		
		try {
			ImageIO.write(bufferedImage, "jpg", lamdaFileStream);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//bufferedImage = ImageProcessor.resizeImageFixed(bufferedImage, size, true);
		//Processing buffered Image to Out File Stream
		
        String aws_s3_put_object_bucket_name = external_media_parent_media_data.get("bucket_name").toString();
        String aws_s3_put_object_key_name = external_media_parent_media_data.get("file_value").toString();
        String aws_s3_put_object_key_permission = "PublicRead";
        this.uploadToS3Bucket(lamdaFileStream,aws_s3_put_object_key_name,aws_s3_put_object_bucket_name,aws_s3_put_object_key_permission);
        
        
        //Communicating with server for process progress acknowledgment
        
        request_parameters = new HashMap<String, String>();
		request_parameters.put("type", "BASE_MEDIA_UPLOADED_TO_S3");
		request_parameters.put("parent_media_id", external_media_parent_media_data.get("id").toString());
		sendProgressCallback(request_parameters);
				        
        
        //globalParamtersList.get("thumbnail_sizes")
        
        String thumbnail_sizes =  globalParamtersList.get("thumbnail_sizes");
        
        String mod_thumbnail_sizes = thumbnail_sizes.replace("[","").replace("]","").replace("\"","");
        
        this.generateThumbnails(mod_thumbnail_sizes,external_media_parent_media_data,bufferedImage);
        
        request_parameters = new HashMap<String, String>();
		request_parameters.put("type", "FINISHING_PROCESS");
		request_parameters.put("parent_media_id", external_media_parent_media_data.get("id").toString());
		sendProgressCallback(request_parameters);
	
	}
	
	public static void generateThumbnails(String mod_thumbnail_sizes,JSONObject external_media_parent_media_data,BufferedImage generated_buffer)
	{
		

		
		List<String> thumbnail_sizes = Arrays.asList(mod_thumbnail_sizes.split(","));
		
		ArrayList thumbFiles = new ArrayList<String>();
		
    	try {
	
			
			for(int x=0;x<=thumbnail_sizes.size()-1;x++)
			{
				//SnippetFunctionHandler.context.getLogger().log();
				
				String _size_string = thumbnail_sizes.get(x);
				String[] _sizes = _size_string.split("x");
				
				
				int sizeX = Integer.parseInt(_sizes[0]);
				int sizeY = Integer.parseInt(_sizes[1]);
				    			
				File localThumbFileStream = new File("/tmp/thumb_"+_size_string+"_"+external_media_parent_media_data.get("file_value").toString());
				
				if(localThumbFileStream.exists()) {
					localThumbFileStream.delete();
		        }
				
				//add local temp filenames to list for deletion later
				thumbFiles.add(localThumbFileStream.getPath());
				context.getLogger().log("Thumb file name - " + localThumbFileStream.getPath() + "\n");
				
				BufferedImage tempBuffered = ScalrImageProcessor.generateThumbnail(generated_buffer, sizeX, sizeY); 
				//BufferedImage tempBuffered = ImageProcessor.generateThumbnail(generated_buffer, sizeX, sizeY);
				context.getLogger().log("Thumbnail created - " + localThumbFileStream.getName() + "\n");
				
		        String aws_s3_put_object_bucket_name = globalParamtersList.get("thumbnail_bucket").toString();
		        String aws_s3_put_object_key_name = _size_string+"_"+external_media_parent_media_data.get("file_value").toString();
		        String aws_s3_put_object_key_permission = "PublicRead";
		        
		        ImageIO.write(tempBuffered, "jpeg", localThumbFileStream);
		        
		        uploadToS3Bucket(localThumbFileStream,aws_s3_put_object_key_name,aws_s3_put_object_bucket_name,aws_s3_put_object_key_permission);
		        
		        //Communicating with server for process progress acknowledgment
		        
		        Map<String, String> request_parameters = new HashMap<String, String>();
		        request_parameters.put("type", "MEDIA_IMAGE_THUMBNAIL_PROCESS_COMPLETE");
		        request_parameters.put("media_id", external_media_parent_media_data.get("id").toString());
		        request_parameters.put("size_string", _size_string);
		        sendProgressCallback(request_parameters);
		        
		        
			}
		
    	} catch (Exception ex) {
    		
			try {
				throw ex;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}finally {
			
			//removing thumbnail tem files
			if(thumbFiles.size() > 0){
        		
    			for(int x=0;x<=thumbFiles.size()-1;x++)
    			{
    				
    				File tmpFile = new File(thumbFiles.get(x).toString());
        			if(tmpFile.exists())
        			{
        				tmpFile.delete();
        			}
    				
    			}
        		
        	}
		}
        
		
	}
	
	
	public static BufferedImage toBufferedImage(Image img)
	{
	    if (img instanceof BufferedImage)
	    {
	        return (BufferedImage) img;
	    }

	    // Create a buffered image with transparency
	    BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

	    // Draw the image on to the buffered image
	    Graphics2D bGr = bimage.createGraphics();
	    bGr.drawImage(img, 0, 0, null);
	    bGr.dispose();
	    return bimage;
	}
	
	
	private Image readImageFromRemoteUrl(String imageUrl) {
		
		Image image = null;
		try {
		    URL url = new URL(imageUrl);
		    image = ImageIO.read(url);
		} catch (IOException e) {
		}
		
		return image;
	}
    
	private void setGlobalParametersList(Object input) {
		
		JSONParser parser = new JSONParser();
        String input_stringified = input.toString();
        
        Object input_parsed;
        
        context.getLogger().log("Louis Master");
      
		try {
			
			input_parsed = parser.parse(input_stringified);
			
			JSONObject input_parsed_object = (JSONObject) input_parsed;
			
			Set parsed_object_keyset = input_parsed_object.keySet();
			
			Object[] keyset_array = parsed_object_keyset.toArray();
			
			 for (Object obj : keyset_array) {
				 
				 String parameter_key = obj.toString();
				 
				 globalParamtersList.put(parameter_key, input_parsed_object.get(parameter_key).toString());
				 
			 }
			 
		} catch (ParseException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
	}
	
	public static JSONObject sendProgressCallback(Map request_parameters)
	{
		
		context.getLogger().log(Integer.toString(request_parameters.size()));
		context.getLogger().log(request_parameters.toString());
		
		Iterator it = request_parameters.entrySet().iterator();
	    
	    HttpClient httpclient = HttpClients.createDefault();
	    
	    String http_post_url = globalParamtersList.get("core_base_url")+globalParamtersList.get("progress_callback_url");
	    
	    context.getLogger().log(http_post_url);
	    
	    HttpPost httppost = new HttpPost(http_post_url);

	    // Request parameters and other properties.
	    List<NameValuePair> params = new ArrayList<NameValuePair>(request_parameters.size());
	    
	    while (it.hasNext()) {
	        Map.Entry pair = (Map.Entry)it.next();
	        
	        params.add(new BasicNameValuePair(pair.getKey().toString(), pair.getValue().toString()));
	        
	        it.remove(); // avoids a ConcurrentModificationException
	        
	        
	    }
	    
	    try {
			httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
			
		    HttpResponse response;
			try {
				response = httpclient.execute(httppost);
				
			    HttpEntity entity = response.getEntity();

			    if (entity != null) {
			        InputStream instream = entity.getContent();
			        
			        BufferedReader buf = new BufferedReader(new InputStreamReader(instream,"UTF-8"));
			        if(response.getStatusLine().getStatusCode()!=HttpStatus.SC_OK)
			        {
			            try {
						
			            	throw new Exception(response.getStatusLine().getReasonPhrase());
						
			            } catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
			        }
			        StringBuilder sb = new StringBuilder();
			        String s;
			        while(true )
			        {
			            s = buf.readLine();
			            if(s==null || s.length()==0)
			                break;
			            sb.append(s);

			        }
			        buf.close();
			        instream.close();
			        
			        
			        JSONParser parser = new JSONParser();
			        
			        String response_stringified = sb.toString();
			        
			        context.getLogger().log(response_stringified);
			        
			        Object input_parsed;
			        
					try {
						
						input_parsed = parser.parse(response_stringified);
						
						JSONObject input_parsed_object = (JSONObject) input_parsed;
						
						return input_parsed_object;
						
					} catch (ParseException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					
			    }
				
			} catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	    
	    return null;
		
	}
	
	public static void uploadToS3Bucket(File file_stream,String aws_s3_put_object_key_name,String aws_s3_put_object_bucket_name,String aws_s3_put_object_permission)
	{

		
		String aws_access_key = globalParamtersList.get("aws_access_key");
		String aws_secret_key = globalParamtersList.get("aws_secret_key");
        BasicAWSCredentials basic_aws_credentials_obj = new BasicAWSCredentials(aws_access_key, aws_secret_key);
        AmazonS3 aws_s3_client_obj = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(basic_aws_credentials_obj))
                .withRegion("us-east-1")
                .build();

        
        //Generating Object Permission        
        CannedAccessControlList s3_key_permission;
        
		switch (aws_s3_put_object_permission) {
		case "PublicRead":
			s3_key_permission = CannedAccessControlList.PublicRead;
		         break;
		case "PublicReadWrite":
			s3_key_permission = CannedAccessControlList.PublicReadWrite;
        break;
		default:
			s3_key_permission = CannedAccessControlList.Private;
		             break;
		}
        
        try {

        	aws_s3_client_obj.putObject(new PutObjectRequest(
	        		aws_s3_put_object_bucket_name, aws_s3_put_object_key_name, file_stream).withCannedAcl(s3_key_permission));

        	context.getLogger().log("File Uploaded to s3 bucket : "+aws_s3_put_object_bucket_name+" with key "+aws_s3_put_object_key_name+" Successfuly");
        	
         } catch (AmazonServiceException ase) {
        	 context.getLogger().log("Caught an AmazonServiceException, which " +
            		"means your request made it " +
                    "to Amazon S3, but was rejected with an error response" +
                    " for some reason.");
        	 context.getLogger().log("Error Message:    " + ase.getMessage());
        	 context.getLogger().log("HTTP Status Code: " + ase.getStatusCode());
        	 context.getLogger().log("AWS Error Code:   " + ase.getErrorCode());
        	 context.getLogger().log("Error Type:       " + ase.getErrorType());
        	 context.getLogger().log("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
        	context.getLogger().log("Caught an AmazonClientException, which " +
            		"means the client encountered " +
                    "an internal error while trying to " +
                    "communicate with S3, " +
                    "such as not being able to access the network.");
        	context.getLogger().log("Error Message: " + ace.getMessage());
        }
		
		
	}
	
}