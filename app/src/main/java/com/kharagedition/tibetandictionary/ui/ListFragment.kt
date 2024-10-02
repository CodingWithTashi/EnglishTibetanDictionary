package com.kharagedition.tibetandictionary.ui

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.View.*
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.SearchAutoComplete
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textview.MaterialTextView
import com.kharagedition.tibetandictionary.MainActivity
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.adapter.WordsPagingDataAdapter
import com.kharagedition.tibetandictionary.util.Constant
import com.kharagedition.tibetandictionary.viewmodel.WordsViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.lang.reflect.Field
import java.util.*
import kotlin.concurrent.schedule


class ListFragment : Fragment() {
    lateinit var commonToolbar: MaterialToolbar
    lateinit var mAdView: AdView
    lateinit var wordRecyclerView: RecyclerView
    lateinit var loadingProgress: ProgressBar
    lateinit var emptyMessage: MaterialTextView
    private val wordsViewModel: WordsViewModel by activityViewModels()
    private val pagingAdapter by lazy { WordsPagingDataAdapter(wordsViewModel) }
     private var diplayFavWordsFavourite: Boolean? = false
    private var mInterstitialAd: InterstitialAd? = null
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view =  inflater.inflate(R.layout.fragment_list, container, false)
        diplayFavWordsFavourite = arguments?.getBoolean("favourite")

        initViews(view)
        setHasOptionsMenu(true)
        val adRequest = AdRequest.Builder().build()
        loadAdsIfNotPurchased(adRequest)

        addListener()
        return view
    }

    private fun loadAdsIfNotPurchased(adRequest: AdRequest) {
        val prefs: SharedPreferences? = activity?.getSharedPreferences("com.kharagedition.dictionary", MODE_PRIVATE)
        val isPurchased = prefs?.getBoolean(Constant.PURCHASED, false)
        //IF PURCHASED DONT KNOW ADS
        if(isPurchased==null || !isPurchased){
            //IF FAV PAGE<SHOW BANNER ADS ONLY
            if(diplayFavWordsFavourite==true){
                mAdView.loadAd(adRequest)
            }
            else{
                //IF LIST PAGE<SHOW BANNINTERSTITIALER ADS ONLY
                mAdView.visibility=GONE
                Handler(Looper.getMainLooper()).postDelayed({
                    loadInterstitialAds(adRequest);
                }, 7000)

            }

        }else{
            mAdView.visibility=GONE
        }
    }

    private fun loadInterstitialAds(adRequest: AdRequest) {
        InterstitialAd.load(requireContext(),Constant.PROD_INTERSTITIAL_AD_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("TAG", adError.message)
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d("TAG", "Ad was loaded.")
                mInterstitialAd = interstitialAd

                if (mInterstitialAd != null) {
                    mInterstitialAd?.show(requireActivity())
                } else {
                    Log.d("TAG", "The interstitial ad wasn't ready yet.")
                }
            }
        })
        mInterstitialAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("TAG", "Ad was dismissed.")
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                Log.d("TAG", "Ad failed to show.")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d("TAG", "Ad showed fullscreen content.")
                mInterstitialAd = null;
            }
        }


    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        if(diplayFavWordsFavourite==null || diplayFavWordsFavourite == false){
            inflater.inflate(R.menu.option_menu, menu)
            val searchItem = menu.findItem(R.id.action_search)
            val searchView = searchItem?.actionView as? SearchView ?: return

// Set up SearchView appearance
            searchView.apply {
                queryHint = "Search..."
            }

// Set up SearchView listeners
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let { safeQuery ->
                        wordsViewModel.filterData(safeQuery)
                        wordsViewModel.liveQuery.observe(viewLifecycleOwner) {
                            fetchWordsFromDictionary(query = true)
                        }
                        Log.i(MainActivity.TAG, "Query submitted: $safeQuery")
                    }
                    return false
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    if (newText.length > 2) {
                        wordsViewModel.filterData(newText)
                        wordsViewModel.liveQuery.observe(viewLifecycleOwner) {
                            fetchWordsFromDictionary(query = true)
                        }
                    }
                    return true
                }
            })

            searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    emptyMessage.visibility = View.GONE
                    fetchWordsFromDictionary(false)
                    return true
                }
            })
        }else{
            commonToolbar.title = "Favourite"
        }

        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                activity?.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initViews(view: View) {
        commonToolbar = view.findViewById(R.id.list_toolbar)
        loadingProgress = view.findViewById(R.id.loading_words)
        emptyMessage = view.findViewById(R.id.empty_msg)
        (activity as AppCompatActivity?)!!.setSupportActionBar(commonToolbar)
        (activity as AppCompatActivity?)!!.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as AppCompatActivity?)!!.supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_baseline_arrow_back_ios_24)


        wordRecyclerView = view.findViewById(R.id.list_container)
        mAdView = view.findViewById(R.id.bannerAd1)
        loadingProgress.visibility = VISIBLE;
        emptyMessage.visibility = GONE;

    }

    private fun addListener() {

        wordRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = pagingAdapter
            //edgeEffectFactory = BounceEdgeEffectFactory()
            setHasFixedSize(true)
        }

        Log.d(MainActivity.TAG, "addListener: ")
        fetchWordsFromDictionary(query = false)
        checkEmptyWordListener();

    }

    private fun fetchWordsFromDictionary(query: Boolean) {

        lifecycleScope.launch {
            if(diplayFavWordsFavourite!=null && diplayFavWordsFavourite==true){
                wordsViewModel.favWordsList.collectLatest {
                    if(loadingProgress.isVisible){
                        loadingProgress.visibility = GONE;
                    }
                    pagingAdapter.submitData(it)
                    pagingAdapter.notifyDataSetChanged();
                }
            }else if (query) {
                wordsViewModel.queryWordsList.collectLatest {
                    if (loadingProgress.isVisible) {
                        loadingProgress.visibility = GONE;
                    }
                    pagingAdapter.submitData(it)
                    pagingAdapter.notifyDataSetChanged();
                }
            }else{
                wordsViewModel.wordsList.collectLatest {
                    if(loadingProgress.isVisible){
                        loadingProgress.visibility = GONE;
                    }
                    pagingAdapter.submitData(it)
                    pagingAdapter.notifyDataSetChanged();
                }
            }


        }


    }

    private fun checkEmptyWordListener() {
        lifecycleScope.launch {
            //Your adapter's loadStateFlow here
            pagingAdapter.loadStateFlow.
            distinctUntilChangedBy {
                it.refresh
            }.collect {
                val list = pagingAdapter.snapshot()
                if ( list.size< 1){
                    emptyMessage.visibility= VISIBLE
                }else{
                    emptyMessage.visibility = GONE
                }

            }
        }

    }

}