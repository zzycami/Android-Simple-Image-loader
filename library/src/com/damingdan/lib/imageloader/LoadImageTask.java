package com.damingdan.lib.imageloader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.damingdan.lib.imageloader.IoUtils.CopyStreamListener;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

public class LoadImageTask implements Runnable, CopyStreamListener {
	protected static final String TAG = "LoadImageTask";
	protected static final boolean DEBUG = true;
	
	protected String url;
	protected MemoryCache memoryCache;
	protected FileCache fileCache;
	protected DisplayImageOptions displayImageOptions;
	protected ImageLoadingListener loadingListener;
	protected ImageLoadingProgressListener progressListener;
	protected static ImageDownloader imageDownloader = new ImageDownloader();
	protected static ImageDecoder imageDecoder = new ImageDecoder();
	protected static Handler handler = new Handler(Looper.getMainLooper());
	
	public LoadImageTask(String url,
			DisplayImageOptions displayImageOptions,
			MemoryCache memoryCache,
			FileCache fileCache,
			ImageLoadingListener loadingListener,
			ImageLoadingProgressListener progressListener) {
		this.url = url;
		this.displayImageOptions = displayImageOptions;
		this.memoryCache = memoryCache;
		this.fileCache = fileCache;
		this.loadingListener = loadingListener;
		this.progressListener = progressListener;
	}

	@Override
	public void run() {
		if(DEBUG) Log.i(TAG, "task start url=" + url);
		Bitmap bitmap;
		try {
			checkTaskNotActual();
			bitmap = memoryCache.get(url);
			if(bitmap == null) {
				bitmap = loadBitmap();
				checkTaskNotActual();
			}
			onComplete(bitmap);
		} catch(TaskCancelledException e) {
			if(DEBUG) Log.i(TAG, "task cancelled url=" + url);
			onCancelled();
		} catch(IOException e) {
			if(DEBUG) Log.e(TAG, "IOException\n" + Log.getStackTraceString(e));
			onFailed(e);
		} catch(Exception e) {
			if(DEBUG) Log.e(TAG, "Exception\n" + Log.getStackTraceString(e));
			onFailed(e);
		}
	}
	
	private Bitmap loadBitmap() throws IOException, TaskCancelledException {
		if(DEBUG) Log.i(TAG, "loadBitmap url=" + url);
		File imageFile = fileCache.get(url);
		if(!imageFile.exists()) {
			requestAndStoreImage(imageFile);
			checkTaskNotActual();
		}
		return decodeAndCacheImage(imageFile);
	}
	
	private void requestAndStoreImage(File imageFile) throws IOException {
		if(DEBUG) Log.i(TAG, "requestAndStoreImage url=" + url);
		InputStream is = imageDownloader.getStream(url);
		try {
			IoUtils.copyStreamToFile(is, imageFile, progressListener == null ? null : this);
		} catch(IOException e) {
			imageFile.delete();
			throw e;
		}
		fileCache.put(url, imageFile);
	}
	
	private Bitmap decodeAndCacheImage(File imageFile) throws IOException {
		if(DEBUG) Log.i(TAG, "decodeAndCacheImage url=" + url);
		Bitmap bitmap = imageDecoder.decode(imageFile);
		if(displayImageOptions.isCacheInMemory()) {
			memoryCache.put(url, bitmap);
		}
		return bitmap;
	}
	
	private void checkTaskNotActual() throws TaskCancelledException {
		if(isTaskNotActual()) {
			throw new TaskCancelledException();
		}
	}
	
	/**
	 * 任务是否不必继续
	 */
	protected boolean isTaskNotActual() {
		return false;
	}
	
	protected View getView() {
		return null;
	}
	
	protected void displayImage(Bitmap bitmap) {
		
	}
	
	private void onCancelled() {
		if(loadingListener != null) {
			handler.post(new Runnable() {

				@Override
				public void run() {
					loadingListener.onLoadingCancelled(url, getView());
				}
				
			});
		}
	}
	
	private void onFailed(final Exception e) {
		if(loadingListener != null) {
			handler.post(new Runnable() {

				@Override
				public void run() {
					loadingListener.onLoadingFailed(url, getView(), e);
				}
				
			});
		}
	}
	
	private void onComplete(final Bitmap bm) {
		if(loadingListener != null) {
			handler.post(new Runnable() {

				@Override
				public void run() {
					View v = getView();
					if(isTaskNotActual()) {
						loadingListener.onLoadingCancelled(url, v);
					} else {
						displayImage(bm);
						loadingListener.onLoadingComplete(url, v, bm);
					}
				}
				
			});
		}
	}

	@Override
	public void onBytesCopied(final int current, final int total) {
		if(progressListener != null) {
			handler.post(new Runnable() {

				@Override
				public void run() {
					progressListener.onProgressUpdate(url, getView(), current, total);
				}
				
			});
		}
		
	}

	private class TaskCancelledException extends Exception {
		private static final long serialVersionUID = 1L;
	}

}
