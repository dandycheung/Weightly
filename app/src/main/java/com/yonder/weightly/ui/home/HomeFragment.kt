package com.yonder.weightly.ui.home

import android.os.Bundle
import android.view.*
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.ads.AdRequest
import com.yonder.statelayout.State
import com.yonder.weightly.R
import com.yonder.weightly.databinding.FragmentHomeBinding
import com.yonder.weightly.ui.home.adapter.HomeInfoCardAdapter
import com.yonder.weightly.ui.home.adapter.HomeInfoCardCreator
import com.yonder.weightly.ui.home.chart.ChartFeeder
import com.yonder.weightly.ui.home.chart.ChartInitializer
import com.yonder.weightly.ui.home.chart.LimitLineFeeder
import com.yonder.weightly.utils.enums.ChartType
import com.yonder.weightly.utils.extensions.safeNavigate
import com.yonder.weightly.utils.setSafeOnClickListener
import com.yonder.weightly.utils.viewBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {
    private val binding by viewBinding(FragmentHomeBinding::bind)

    private val viewModel: HomeViewModel by viewModels()

    private val adapter: HomeInfoCardAdapter by lazy {
        HomeInfoCardAdapter()
    }

    lateinit var menuHost: MenuHost

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        observe()
        initAdListener()
    }

    private fun initViews() {
        menuHost = requireActivity()
        with(binding) {
            with(rvInfoCard) {
                layoutManager = GridLayoutManager(requireContext(), 3)
                adapter = this@HomeFragment.adapter
                itemAnimator = null
            }

            ChartInitializer.initLineChart(lineChart)
            ChartInitializer.initBarChart(barChart)
            btnRemoveAds.setSafeOnClickListener {
                viewModel.startBilling(requireActivity())
            }
            btnAddWeightForToday.setSafeOnClickListener {
                safeNavigate(
                    HomeFragmentDirections.actionNavigateAddWeight(
                        selectedDate = null,
                        weight = null,
                    ),
                )
            }
        }
    }

    private fun observe() {
        lifecycleScope.launchWhenStarted {
            viewModel.uiState.collect(::setUIState)
        }
    }

    private fun initAdListener() {
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)
    }

    private fun setUIState(uiState: HomeViewModel.UiState) =
        with(binding) {
            if (uiState.shouldShowEmptyView) {
                stateLayout.setState(State.EMPTY)
            } else {
                stateLayout.setState(State.CONTENT)
                binding.adView.isVisible = uiState.shouldShowAds
                btnRemoveAds.isVisible = uiState.shouldShowAds
                binding.btnAddWeightForToday.isVisible = uiState.shouldShowAddWeightForTodayButton
                if (uiState.chartType == ChartType.LINE) {
                    lineChart.isVisible = true
                    barChart.isVisible = false
                    ChartFeeder.setLineChartData(
                        chart = lineChart,
                        histories = uiState.histories,
                        barEntries = uiState.barEntries,
                        context = requireContext(),
                    )
                } else {
                    lineChart.isVisible = false
                    barChart.isVisible = true
                    ChartFeeder.setBarChartData(
                        chart = barChart,
                        histories = uiState.histories,
                        barEntries = uiState.barEntries,
                        context = requireContext(),
                    )
                }

                if (uiState.shouldShowLimitLine) {
                    LimitLineFeeder.addLimitLineToLineChart(
                        requireContext(),
                        lineChart,
                        uiState.averageWeight?.toFloatOrNull(),
                        uiState.goalWeight?.toFloatOrNull(),
                    )
                    LimitLineFeeder.addLimitLineToBarChart(
                        requireContext(),
                        barChart,
                        uiState.averageWeight?.toFloatOrNull(),
                        uiState.goalWeight?.toFloatOrNull(),
                    )
                } else {
                    LimitLineFeeder.removeLimitLines(lineChart = lineChart, barChart = barChart)
                }
                val infoCardList = HomeInfoCardCreator.create(uiState)
                adapter.submitList(infoCardList)
                uiState.userGoal?.run(tvGoalDescription::setText)
            }
        }

    private val menuProvider: MenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.home_menu, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.action_add -> {
                    safeNavigate(
                        HomeFragmentDirections.actionNavigateAddWeight(
                            null,
                            null,
                        ),
                    )
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchHome()
        viewModel.checkIsPremiumUser()
        viewModel.hasUserWeightForToday()
        menuHost.addMenuProvider(menuProvider)
    }

    override fun onStop() {
        super.onStop()
        viewModel.cancelJobs()
        menuHost.removeMenuProvider(menuProvider)
    }


}
