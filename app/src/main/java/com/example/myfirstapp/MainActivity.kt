package com.example.myfirstapp

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import coil.load
// FIXED: This import was missing, causing the "Unresolved reference" error.
import com.example.myfirstapp.AppDatabase
import com.example.myfirstapp.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val glicko = Glicko2()

    // --- Data Storage ---
    private var imageList = mutableListOf<RatedImage>()
    private var fileUriMap = mutableMapOf<String, Uri>()
    private var currentPair: Pair<RatedImage, RatedImage>? = null
    private var lastPair: Pair<RatedImage, RatedImage>? = null

    // --- Database ---
    private val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    // --- Activity Launchers ---
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            loadImagesFromFolder(uri)
        } else {
            Toast.makeText(this, "No folder selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- UI Click Listeners ---
        binding.leftImageView.setOnClickListener { rateImage(0) }
        binding.rightImageView.setOnClickListener { rateImage(1) }
        binding.openFolderButton.setOnClickListener { folderPickerLauncher.launch(null) }
        binding.rankingsButton.setOnClickListener { showRankings() }
    }

    private fun loadImagesFromFolder(folderUri: Uri) {
        lifecycleScope.launch {
            imageList.clear()
            fileUriMap.clear()
            val supportedMimeTypes = listOf("image/jpeg", "image/png", "image/gif", "image/bmp")

            val treeId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )

            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeTypeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val fileName = cursor.getString(nameIndex)
                    val mimeType = cursor.getString(mimeTypeIndex)

                    if (supportedMimeTypes.contains(mimeType)) {
                        val docId = cursor.getString(idIndex)
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        fileUriMap[fileName] = fileUri

                        val existingImage = database.ratedImageDao().getImage(fileName)
                        if (existingImage != null) {
                            imageList.add(existingImage)
                        } else {
                            imageList.add(RatedImage(fileName = fileName, playerStats = Player()))
                        }
                    }
                }
            }

            if (imageList.size >= 2) {
                Toast.makeText(this@MainActivity, "Found ${imageList.size} images!", Toast.LENGTH_SHORT).show()
                fetchNextPair()
            } else {
                Toast.makeText(this@MainActivity, "Fewer than 2 images found.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun fetchNextPair() {
        if (imageList.size < 2) return

        var newPair: Pair<RatedImage, RatedImage>
        do {
            val randomPair = imageList.shuffled().take(2)
            newPair = Pair(randomPair[0], randomPair[1])
        } while (newPair == lastPair && imageList.size > 2)

        currentPair = newPair
        lastPair = newPair
        displayPair()
    }

    private fun displayPair() {
        val pair = currentPair ?: return
        val leftFileUri = fileUriMap[pair.first.fileName]
        val rightFileUri = fileUriMap[pair.second.fileName]

        binding.leftImageView.load(leftFileUri) {
            placeholder(android.R.color.darker_gray)
            error(android.R.color.holo_red_dark)
        }
        binding.rightImageView.load(rightFileUri) {
            placeholder(android.R.color.darker_gray)
            error(android.R.color.holo_red_dark)
        }
    }

    private fun rateImage(choice: Int) {
        val pair = currentPair ?: return

        val winner = if (choice == 0) pair.first else pair.second
        val loser = if (choice == 0) pair.second else pair.first
        val oldWinnerRating = winner.playerStats.rating

        val (winnerResult, loserResult) = glicko.updateRatings(winner.playerStats, loser.playerStats)

        val updatedWinner = winner.copy(
            playerStats = winner.playerStats.copy(
                rating = winnerResult.rating,
                rd = winnerResult.rd,
                vol = winnerResult.vol,
                wins = winner.playerStats.wins + 1
            )
        )
        imageList[imageList.indexOfFirst { it.fileName == winner.fileName }] = updatedWinner

        val updatedLoser = loser.copy(
            playerStats = loser.playerStats.copy(
                rating = loserResult.rating,
                rd = loserResult.rd,
                vol = loserResult.vol,
                losses = loser.playerStats.losses + 1
            )
        )
        imageList[imageList.indexOfFirst { it.fileName == loser.fileName }] = updatedLoser

        lifecycleScope.launch {
            database.ratedImageDao().upsertImage(updatedWinner)
            database.ratedImageDao().upsertImage(updatedLoser)
        }

        val ratingChange = winnerResult.rating - oldWinnerRating
        val plusSign = if (ratingChange > 0) "+" else ""
        Toast.makeText(this, "Rating change: $plusSign${ratingChange.roundToInt()}", Toast.LENGTH_SHORT).show()

        fetchNextPair()
    }

    private fun showRankings() {
        if (imageList.isEmpty()) {
            Toast.makeText(this, "Please open a folder and rate images first.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, RankingsActivity::class.java)
        intent.putExtra("imageListKey", ArrayList(imageList))
        startActivity(intent)
    }
}