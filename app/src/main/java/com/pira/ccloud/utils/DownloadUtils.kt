package com.pira.ccloud.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.pira.ccloud.VideoPlayerActivity

object DownloadUtils {
    fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            // Show error message when URL cannot be opened
            android.widget.Toast.makeText(context, "Unable to open URL: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Video URL", text)
        clipboard.setPrimaryClip(clip)
        
        // Show a toast or snackbar to indicate success
        android.widget.Toast.makeText(context, "Link copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun openWithADM(context: Context, url: String) {
        try {
            // Try multiple common ADM package names and intent actions
            val packages = arrayOf(
                "com.dv.adm",
                "com.dv.adm.pay",
                "com.dv.get",
                "com.dv.adm.old"
            )
            
            var success = false
            for (pkg in packages) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.`package` = pkg
                    context.startActivity(intent)
                    success = true
                    break
                } catch (e: Exception) {
                    // Try next package
                }
            }
            
            // If none of the specific packages work, try the general approach
            if (!success) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("adm://$url"))
                    context.startActivity(intent)
                    success = true
                } catch (e: Exception) {
                    // Continue to fallback
                }
            }
            
            // If all else fails, show error message
            if (!success) {
                android.widget.Toast.makeText(context, "ADM is not installed on this device", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            // If ADM is not installed, show error message
            android.widget.Toast.makeText(context, "ADM is not installed on this device", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun openWithVLC(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse(url), "video/*")
            intent.setPackage("org.videolan.vlc")
            context.startActivity(intent)
        } catch (e: Exception) {
            // setPackage + setType only succeeds if VLC's manifest happens to declare
            // an intent-filter that matches "video/*" exactly. Some VLC builds/forks
            // register slightly different data specs, so a plain ActivityNotFound
            // here does NOT reliably mean "VLC isn't installed" - it can also mean
            // "installed, but the direct match failed". Fall back to the chooser,
            // which asks PackageManager to resolve more broadly, before giving up.
            try {
                openWithChooser(context, url)
            } catch (e2: Exception) {
                android.widget.Toast.makeText(context, "VLC Player not installed", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun openWithMXPlayer(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse(url), "video/*")
            intent.setPackage("com.mxtech.videoplayer.ad") // MX Player Free
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Try pro version
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.parse(url), "video/*")
                intent.setPackage("com.mxtech.videoplayer.pro") // MX Player Pro
                context.startActivity(intent)
            } catch (e2: Exception) {
                // Same reasoning as openWithVLC above: fall back to the chooser
                // instead of assuming the app is missing.
                try {
                    openWithChooser(context, url)
                } catch (e3: Exception) {
                    android.widget.Toast.makeText(context, "MX Player not installed", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Generic fallback that lets the user pick from ANY video-capable app
    // installed on the device, instead of relying on a hardcoded list of
    // package names. This covers "the rest of the apps" that VLC/MX/KM
    // don't - e.g. an Android TV specific player, a different fork, etc.
    // Using Intent.createChooser() also sidesteps Android 11+ package
    // visibility restrictions: the system resolves and shows matching apps
    // on our behalf, so we don't need a <queries> entry for every possible
    // player someone might have installed.
    fun openWithChooser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse(url), "video/*")
            val chooser = Intent.createChooser(intent, "Open video with")
            context.startActivity(chooser)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "No app found to play this video", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun openWithKMPlayer(context: Context, url: String) {
        // Try multiple KM Player package names
        val packages = arrayOf(
            "com.kmplayer", // KM Player
            "com.kmplayerpro",
            "com.kmplayer.google",
            "com.kmplayer.d"
        )
        
        var success = false
        for (pkg in packages) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.parse(url), "video/*")
                intent.setPackage(pkg)
                context.startActivity(intent)
                success = true
                break
            } catch (e: Exception) {
                // Try next package
            }
        }
        
        // If none of the specific packages work, fall back to the chooser (broader
        // PackageManager resolution) before concluding nothing is installed.
        if (!success) {
            try {
                openWithChooser(context, url)
            } catch (e: Exception) {
                // If all else fails, show error message
                android.widget.Toast.makeText(context, "KM Player not installed", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}