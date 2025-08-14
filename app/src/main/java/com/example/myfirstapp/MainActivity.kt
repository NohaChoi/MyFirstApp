package com.example.myfirstapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.myfirstapp.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.Stack
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val glicko = Glicko2()

    // --- Data Storage ---
    private var imageList = mutableListOf<RatedImage>()
    private var fileUriMap = mutableMapOf<String, Uri>()
    private var currentPair: Pair<RatedImage, RatedImage>? = null
    private var lastPair: Pair<RatedImage, RatedImage>? = null
    private val undoStack = Stack<Pair<RatedImage, RatedImage>>()
    private var selectedMode = "standard"

    // --- Database ---
    // This will now be set when a folder is loaded.
    private var database: AppDatabase? = null

    // --- Settings ---
    // Use SharedPreferences to save the last folder's URI.
    private val prefs by lazy { getSharedPreferences("ImageRankerPrefs", Context.MODE_PRIVATE) }

    // --- Activity Launchers ---
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // When a new folder is picked, save its URI string to our preferences.
            prefs.edit().putString("last_folder_uri", uri.toString()).apply()

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
        binding.undoButton.setOnClickListener { undoLastRating() }

        // --- Auto-load on startup ---
        loadLastUsedFolder()
    }

    /**
     * NEW: Checks SharedPreferences for a saved folder URI and loads it.
     */
    private fun loadLastUsedFolder() {
        val lastFolderUriString = prefs.getString("last_folder_uri", null)
        if (lastFolderUriString != null) {
            val folderUri = Uri.parse(lastFolderUriString)
            // Ensure we still have permission to read this folder.
            if (contentResolver.persistedUriPermissions.any { it.uri == folderUri }) {
                loadImagesFromFolder(folderUri)
            }
        }
    }

    /**
     * UPGRADED: Now creates a folder-specific database before loading images.
     */
    private fun loadImagesFromFolder(folderUri: Uri) {
        // Get a name for the database from the folder's name.
        val folderName = getFolderName(folderUri)
        if (folderName == null) {
            Toast.makeText(this, "Could not determine folder name.", Toast.LENGTH_SHORT).show()
            return
        }
        // Get the database instance for this specific folder.
        database = AppDatabase.getDatabase(this, "ratings_$folderName.db")

        lifecycleScope.launch {
            imageList.clear()
            fileUriMap.clear()
            undoStack.clear()
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

                        val existingImage = database!!.ratedImageDao().getImage(fileName)
                        if (existingImage != null) {
                            imageList.add(existingImage)
                        } else {
                            imageList.add(RatedImage(fileName = fileName, playerStats = Player()))
                        }
                    }
                }
            }

            if (imageList.size >= 2) {
                Toast.makeText(this@MainActivity, "Loaded ${imageList.size} images from $folderName!", Toast.LENGTH_SHORT).show()
                fetchNextPair()
            } else {
                Toast.makeText(this@MainActivity, "Fewer than 2 images found.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // All other functions (fetchNextPair, displayPair, etc.) remain the same...

    /**
     * Helper function to get the display name of a folder from its URI.
     */
    private fun getFolderName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            } else {
                null
            }
        }
    }

    private fun fetchNextPair() {
        if (imageList.size < 2) {
            binding.leftImageView.load(null)
            binding.rightImageView.load(null)
            return
        }

        var pool = getCurrentImagePool()
        if (pool.size < 2) {
            if (selectedMode != "standard") {
                Toast.makeText(this, "Not enough images for this mode, falling back to Standard.", Toast.LENGTH_SHORT).show()
                selectedMode = "standard"
                pool = getCurrentImagePool()
            }
        }

        val imageA = pool.maxByOrNull { it.playerStats.rd } ?: return
        val windowScale = 2.0
        val lowBound = imageA.playerStats.rating - windowScale * imageA.playerStats.rd
        val highBound = imageA.playerStats.rating + windowScale * imageA.playerStats.rd
        var candidates = pool.filter {
            it.fileName != imageA.fileName &&
                    it.playerStats.rating >= lowBound &&
                    it.playerStats.rating <= highBound
        }

        if (candidates.isEmpty()) {
            candidates = pool.filter { it.fileName != imageA.fileName }
        }

        if (candidates.isEmpty()) return

        var imageB = candidates.random()
        if (lastPair != null && setOf(imageA, imageB) == setOf(lastPair!!.first, lastPair!!.second)) {
            val otherCandidates = candidates.filter { it.fileName != imageB.fileName }
            if (otherCandidates.isNotEmpty()) {
                imageB = otherCandidates.random()
            }
        }

        val newPair = Pair(imageA, imageB)
        currentPair = newPair
        lastPair = newPair
        displayPair()
    }

    private fun getCurrentImagePool(): List<RatedImage> {
        return when (selectedMode) {
            "high_elo" -> imageList.filter { it.playerStats.rating >= HIGH_ELO_THRESHOLD }
            "elite_tier" -> imageList.filter { it.playerStats.rating >= ELITE_TIER_THRESHOLD }
            else -> imageList
        }
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
        val db = database ?: return // Don't do anything if database isn't initialized

        val winner = if (choice == 0) pair.first else pair.second
        val loser = if (choice == 0) pair.second else pair.first

        undoStack.push(Pair(winner.copy(), loser.copy()))

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
            db.ratedImageDao().upsertImage(updatedWinner)
            db.ratedImageDao().upsertImage(updatedLoser)
        }

        val ratingChange = winnerResult.rating - oldWinnerRating
        val plusSign = if (ratingChange > 0) "+" else ""
        Toast.makeText(this, "Rating change: $plusSign${ratingChange.roundToInt()}", Toast.LENGTH_SHORT).show()

        fetchNextPair()
    }

    private fun undoLastRating() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "Nothing to undo.", Toast.LENGTH_SHORT).show()
            return
        }
        val db = database ?: return

        val (previousWinnerState, previousLoserState) = undoStack.pop()

        val winnerIndex = imageList.indexOfFirst { it.fileName == previousWinnerState.fileName }
        if (winnerIndex != -1) {
            imageList[winnerIndex] = previousWinnerState
        }

        val loserIndex = imageList.indexOfFirst { it.fileName == previousLoserState.fileName }
        if (loserIndex != -1) {
            imageList[loserIndex] = previousLoserState
        }

        lifecycleScope.launch {
            db.ratedImageDao().upsertImage(previousWinnerState)
            db.ratedImageDao().upsertImage(previousLoserState)
        }

        currentPair = Pair(previousWinnerState, previousLoserState)
        displayPair()
        Toast.makeText(this, "Last rating undone.", Toast.LENGTH_SHORT).show()
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