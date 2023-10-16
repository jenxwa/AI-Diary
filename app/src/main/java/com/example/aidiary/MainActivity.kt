package com.example.aidiary

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aidiary.databinding.ActivityMainBinding
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val entries: MutableList<DiaryEntry> = mutableListOf()
    private val adapter = EntryAdapter(entries)
    private var httpClient: OkHttpClient? = null

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        return dateFormat.format(Date())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup RecyclerView
        binding.entryRV.layoutManager = LinearLayoutManager(this)
        binding.entryRV.adapter = adapter

        // Load saved entries from storage
        loadEntriesFromStorage()

        // Check if there's an entry for today's date
        val today = getCurrentDate()
        if (entries.none { it.date == today }) {
            entries.add(0, DiaryEntry(today, ""))
            adapter.notifyDataSetChanged()
        }

        // Set the button click listener in each item to send the text to ChatGPT API
        adapter.setOnEntryButtonClickListener { position, content ->
            sendTextToChatGPT(content) { transformedText ->
                entries[position] = DiaryEntry(entries[position].date, transformedText)
                saveEntryToStorage(entries[position])
                adapter.notifyItemChanged(position)
            }
        }
        // Create an OkHttp client with an interceptor for the API key
        httpClient = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .writeTimeout(1, TimeUnit.MINUTES)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header(
                        "Authorization",
                        "Bearer YOUR_API_KEY"
                    ) // replace YOUR_API_KEY with your actual key
                    .method(original.method, original.body)
                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level =
                    HttpLoggingInterceptor.Level.BODY // This will log the request and response bodies
            })
            .build()
    }

    private fun loadEntriesFromStorage() {
        val sharedPreferences = getSharedPreferences(Companion.PREF_NAME, MODE_PRIVATE)
        val count = sharedPreferences.getInt(Companion.ENTRY_COUNT, 0)

        entries.clear()

        for (i in 0 until count) {
            val date = sharedPreferences.getString("EntryDate_$i", "") ?: ""
            val content = sharedPreferences.getString("EntryContent_$i", "") ?: ""
            entries.add(DiaryEntry(date, content))
        }

        adapter.notifyDataSetChanged()
    }

    private fun saveEntryToStorage(entry: DiaryEntry) {
        val sharedPreferences = getSharedPreferences(Companion.PREF_NAME, MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val currentCount = sharedPreferences.getInt(Companion.ENTRY_COUNT, 0)

        editor.putString("EntryDate_$currentCount", entry.date)
        editor.putString("EntryContent_$currentCount", entry.content)
        editor.putInt(Companion.ENTRY_COUNT, currentCount + 1)

        editor.apply()
    }


    private fun sendTextToChatGPT(text: String, onSuccess: (String) -> Unit) {
        // Create Retrofit instance if not already created
        val retrofit = httpClient?.let {
            Retrofit.Builder()
                .baseUrl("https://api.openai.com/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(it)
                .build()
        }

        val service = retrofit?.create(ChatGPTService::class.java) ?: return
        val requestBody = ChatGPTRequest(prompt = text)

        service.transformText(requestBody).enqueue(object : Callback<ChatGPTResponse> {
            override fun onResponse(
                call: Call<ChatGPTResponse>,
                response: Response<ChatGPTResponse>
            ) {
                if (response.isSuccessful) {
                    val transformedText = response.body()?.transformedText ?: ""
                    onSuccess(transformedText)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Unknown Error"
                    Log.e("ChatGPTAPI", "Response failed! Code: ${response.code()}, Message: ${response.message()}")
                    Log.e("ChatGPTAPI", "Full response: $errorMsg")
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to transform text",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<ChatGPTResponse>, t: Throwable) {
                Log.e("ChatGPTAPI", "Error: ${t.localizedMessage}")
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${t.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    companion object {
        private const val PREF_NAME = "DiaryEntries"
        private const val ENTRY_COUNT = "EntryCount"
    }
}

interface ChatGPTService {
    @POST("engines/davinci/completions")
    fun transformText(@Body request: ChatGPTRequest): Call<ChatGPTResponse>
}

data class ChatGPTRequest(val prompt: String, val max_tokens: Int = 4)

data class ChatGPTResponse(val transformedText: String)
