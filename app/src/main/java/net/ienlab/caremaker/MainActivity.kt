package net.ienlab.caremaker

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Html
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.nio.charset.Charset
import kotlin.math.max

val TAG = "CareMakerTAG"

class MainActivity : AppCompatActivity() {

    val PICK_FROM_GALLERY = 7
    lateinit var originImage: Bitmap
    lateinit var originImageCircle: RoundedBitmapDrawable

    lateinit var sharedPreferences: SharedPreferences

    private val TEMP_FILE_NAME = "CareMakerTemp.png"

    val SCALETYPE_ROUND = 3
    val SCALETYPE_NONE = 4
    var scaleTypeCode = SCALETYPE_NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE)

        val adRequest = AdRequest.Builder()
        if (BuildConfig.DEBUG) {
            val testDevices: MutableList<String> = mutableListOf()
            testDevices.add("F06E8B7D6604D51927A36B77876AF8DC")

            val requestConfiguration = RequestConfiguration.Builder()
                .setTestDeviceIds(testDevices)
                .build()
            MobileAds.setRequestConfiguration(requestConfiguration)
        }

        adView.loadAd(adRequest.build())

        val thumbId = arrayOf(R.drawable.ads_bp, R.drawable.ads_ih)
        val thumbLink = arrayOf("net.ienlab.blogplanner", "net.ienlab.ireke")

        ads.setImageResource(thumbId[thumbId.lastIndex])
        ads.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("market://details?id=${thumbLink[thumbId.lastIndex]}")
            startActivity(intent)
        }

        var i = 0

        val r: Runnable = object: Runnable {
            override fun run() {
                ads.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse("market://details?id=${thumbLink[(i + thumbId.lastIndex) % thumbId.size]}")
                    startActivity(intent)
                }
                ads.setImageResource(thumbId[i % thumbId.size])

                i++
                ads.postDelayed(this, 5000)
            }
        }

        ads.postDelayed(r, 5000)

        if (!sharedPreferences.getBoolean("help", false)) {
            val helpDialog = AlertDialog.Builder(this@MainActivity)
            helpDialog.setTitle(R.string.help)
                .setView(R.layout.dialog_help)
                .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
            sharedPreferences.edit().putBoolean("help", true).apply()
        }

        originImage = BitmapFactory.decodeResource(resources, R.drawable.img_care_heart2)
        originImageCircle = RoundedBitmapDrawableFactory.create(resources, originImage)
        originImageCircle.cornerRadius = max(originImage.width, originImage.height) / 2.0f
        originImageCircle.setAntiAlias(true)

        btn_rounded.setOnClickListener {
            if (scaleTypeCode == SCALETYPE_ROUND) {
                scaleTypeCode = SCALETYPE_NONE
                img_care_heart.setImageBitmap(originImage)
                btn_rounded.setIconTintResource(android.R.color.white)
                btn_rounded.setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
            } else if (scaleTypeCode == SCALETYPE_NONE) {
                scaleTypeCode = SCALETYPE_ROUND
                img_care_heart.setImageDrawable(originImageCircle)
                btn_rounded.setIconTintResource(R.color.colorAccent)
                btn_rounded.setBackgroundColor(Color.BLACK)
            }
        }


        // 체인지로그 Dialog

        val changelog_dialog_builder = AlertDialog.Builder(this)
        changelog_dialog_builder.setMessage(getString(R.string.changelog_content))
            .setPositiveButton(R.string.ok) { dialog, id ->
                dialog.cancel()
            }

        val version: String
        try {
            val i = packageManager.getPackageInfo(packageName, 0)
            version = i.versionName
            changelog_dialog_builder.setTitle(String.format("%s %s", getString(R.string.app_name), "$version ${getString(R.string.changelog)}"
            ))
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        val changelog_dialog = changelog_dialog_builder.create()

        try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            val nowVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode.toInt()
            else pi.versionCode
            val getVersion = sharedPreferences.getInt("lastVersion", 0)

            Log.d("VersionCodes", "now" + nowVersion + "get" + getVersion)

            if (nowVersion > getVersion) {
                sharedPreferences.edit().putInt("lastVersion", nowVersion).apply()
                changelog_dialog.show()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        btn_share.setOnClickListener {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                try {
                    savedScale(1)
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace()
                    try {
                        savedScale(2)
                    } catch (e2: OutOfMemoryError) {
                        e2.printStackTrace()
                        savedScale(4)
                    }
                }

            } else {
                Snackbar.make(it, getString(R.string.allow_permission), Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.allow)) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
                    }
                    .show()
            }

        }

        img_care_heart.setOnClickListener { rotateImage() }

        img_care_over.setOnClickListener { rotateImage() }

        img_care_under.setOnClickListener { rotateImage() }
    }

    fun savedScale(divide: Int) {
        val bitmap = Bitmap.createBitmap(1000 / divide, 1000 / divide, Bitmap.Config.ARGB_8888)
        val paint = Paint()
        val canvas = Canvas(bitmap)
        var bitmap_under =
            Bitmap.createScaledBitmap(
                BitmapFactory.decodeResource(resources, R.drawable.img_care_under),
                1000 / divide, 1000 / divide, true
            )

        var bitmap_over =
            Bitmap.createScaledBitmap(
                BitmapFactory.decodeResource(resources, R.drawable.img_care_over),
                1000 / divide, 1000 / divide, true
            )

        canvas.drawBitmap(bitmap_under, 0f, 0f, paint)
//                if (!storage.purchasedAds())
//                    canvas.drawBitmap(bitmap_watermark, 720f, 720f, paint)

        var width = 559
        var height = 559
        if (originImage.width >= originImage.height) {
            height = 559 * originImage.height / originImage.width
        } else {
            width = 559 * originImage.width / originImage.height
        }

        if (scaleTypeCode == SCALETYPE_NONE) {
            val copyImage = Bitmap.createScaledBitmap(originImage, width / divide, height / divide, false)
            canvas.drawBitmap(copyImage, (379 - width / 2).toFloat() / divide, (721 - height / 2).toFloat() / divide, paint)
        } else if (scaleTypeCode == SCALETYPE_ROUND) {
            val copyImageCircle = originImageCircle
            copyImageCircle.setBounds((379 - width / 2) / divide, (721 - height / 2) / divide, (379 + width / 2) / divide, (721 + height / 2) / divide)
            copyImageCircle.draw(canvas)
        }

        canvas.drawBitmap(bitmap_over, 0f, 0f, paint)


        val file = File(cacheDir, TEMP_FILE_NAME)

        Log.d(TAG, "path: ${file.path}")


        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FileProvider.getUriForFile(this, "net.ienlab.caremaker.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }

        val share = Intent(Intent.ACTION_SEND)
        share.type = "image/png"
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)

        intent.putExtra(MediaStore.EXTRA_OUTPUT, file.path)
        share.putExtra(Intent.EXTRA_STREAM, uri)

        if (scaleTypeCode == SCALETYPE_NONE) {
            img_care_heart.setImageBitmap(originImage)
        } else if (scaleTypeCode == SCALETYPE_ROUND) {
            originImageCircle = RoundedBitmapDrawableFactory.create(resources, originImage)
            originImageCircle.cornerRadius = max(originImage.width, originImage.height) / 2.0f
            originImageCircle.setAntiAlias(true)
            img_care_heart.setImageDrawable(originImageCircle)
        }

        startActivity(Intent.createChooser(share, getString(R.string.share)))
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.app_bar_help -> {
                val helpDialog = AlertDialog.Builder(this@MainActivity)
                helpDialog.setTitle(R.string.help)
                    .setView(R.layout.dialog_help)
                    .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }

            R.id.app_bar_import -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    //******call android default gallery
                    intent.type = "image/*"
//                intent.setAction(Intent.ACTION_GET_CONTENT);

                    //******code for crop image
//                intent.putExtra(MediaStore.EXTRA_OUTPUT, mTempImageUri)
                    intent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.toString())
                    startActivityForResult(Intent.createChooser(intent, getString(R.string.choose_image)), PICK_FROM_GALLERY)

                } else { //거절
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        // 다시 보기 체크 X, 권한 거절
                    } else {
                        // 다시 보기 체크 O, 권한 거절
                    }
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
                }
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            PICK_FROM_GALLERY -> {
                try {
                    val imageStream = contentResolver.openInputStream(data!!.data as Uri)
                    val selectedImage = BitmapFactory.decodeStream(imageStream)

                    originImage = selectedImage.copy(Bitmap.Config.ARGB_8888, true)
                    originImageCircle = RoundedBitmapDrawableFactory.create(resources, originImage)
                    originImageCircle.cornerRadius = max(originImage.width, originImage.height) / 2.0f
                    originImageCircle.setAntiAlias(true)

//                    img_care_heart.setImageBitmap(originImage)

                    if (scaleTypeCode == SCALETYPE_ROUND) {
                        img_care_heart.setImageDrawable(originImageCircle)
                    } else if (scaleTypeCode == SCALETYPE_NONE) {
                        img_care_heart.setImageBitmap(originImage)
                    }

                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                    Toast.makeText(this, "에러 1", Toast.LENGTH_LONG).show()
                } catch (e2: RuntimeException) {
                    e2.printStackTrace()
                    Toast.makeText(this, getString(R.string.file_not_chosen), Toast.LENGTH_LONG).show()
                }
            }
            32459 -> {
//                if (resultCode == Activity.RESULT_OK) {
//                    var iabMenuItem = mMenu.findItem(R.id.app_bar_iab)
//                    iabMenuItem.isVisible = false
//                    watermark.visibility = View.GONE
//                    adView.visibility = View.GONE
//                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 0) {
            if (grantResults[0] == 0) {
                // 해당 권한 승낙
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                //******call android default gallery
                intent.type = "image/*"
                intent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.toString())
                startActivityForResult(Intent.createChooser(intent, getString(R.string.choose_image)), PICK_FROM_GALLERY)


            } else {
                // 거절
                Log.d("isPermissionGranted", "falseResult : ${permissions[0]}")
            }
        }
    }

    fun rotateImage() {
        var matrix = Matrix()
        matrix.postRotate(90f)
        var copyImage = originImage
        originImage = Bitmap.createBitmap(copyImage, 0, 0, copyImage.width, copyImage.height, matrix, true)

        originImageCircle = RoundedBitmapDrawableFactory.create(resources, originImage)
        originImageCircle.cornerRadius = max(originImage.width, originImage.height) / 2.0f
        originImageCircle.setAntiAlias(true)

        if (scaleTypeCode == SCALETYPE_NONE) {
            img_care_heart.setImageBitmap(originImage)
        } else if (scaleTypeCode == SCALETYPE_ROUND) {
            img_care_heart.setImageDrawable(originImageCircle)
        }
    }
}
