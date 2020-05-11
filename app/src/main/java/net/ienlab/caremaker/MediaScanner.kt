package net.ienlab.caremaker

import android.content.Context
import android.media.MediaScannerConnection
import android.media.MediaScannerConnection.MediaScannerConnectionClient
import android.net.Uri

class MediaScanner private constructor(private val mContext: Context) {
    private var mPath: String? = null
    private var mMediaScanner: MediaScannerConnection? = null
    private var mMediaScannerClient: MediaScannerConnectionClient? = null
    fun mediaScanning(path: String?) {
        if (mMediaScanner == null) {
            mMediaScannerClient = object : MediaScannerConnectionClient {
                override fun onMediaScannerConnected() {
                    mMediaScanner!!.scanFile(path, null)
                }

                override fun onScanCompleted(
                    path: String,
                    uri: Uri
                ) {
                    println("::::MediaScan Success::::")
                    mMediaScanner!!.disconnect()
                }
            }
            mMediaScanner = MediaScannerConnection(mContext, mMediaScannerClient)
        }
        mPath = path
        mMediaScanner!!.connect()
    }

    companion object {
        fun newInstance(context: Context): MediaScanner {
            return MediaScanner(context)
        }
    }

}