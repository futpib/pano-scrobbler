package com.arn.scrobble

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.setMargins
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.FloatingWindow
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.arn.scrobble.api.Scrobblables
import com.arn.scrobble.api.lastfm.FmException
import com.arn.scrobble.billing.BillingViewModel
import com.arn.scrobble.databinding.ContentMainBinding
import com.arn.scrobble.databinding.HeaderNavBinding
import com.arn.scrobble.search.IndexingWorker
import com.arn.scrobble.themes.ColorPatchUtils
import com.arn.scrobble.ui.UiUtils
import com.arn.scrobble.ui.UiUtils.collectLatestLifecycleFlow
import com.arn.scrobble.ui.UiUtils.dp
import com.arn.scrobble.ui.UiUtils.fadeToolbarTitle
import com.arn.scrobble.ui.UiUtils.focusOnTv
import com.arn.scrobble.ui.UiUtils.setupInsets
import com.arn.scrobble.utils.LocaleUtils.setLocaleCompat
import com.arn.scrobble.utils.NavUtils
import com.arn.scrobble.utils.Stuff
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber


class MainActivity : AppCompatActivity(),
    NavController.OnDestinationChangedListener {

    private val prefs = App.prefs
    lateinit var binding: ContentMainBinding
    private val billingViewModel by viewModels<BillingViewModel>()
    private val mainNotifierViewModel by viewModels<MainNotifierViewModel>()
    private lateinit var navController: NavController
    private var navHeaderbinding: HeaderNavBinding? = null
    private lateinit var mainFab: View

    override fun onCreate(savedInstanceState: Bundle?) {
        var canShowNotices = false

        super.onCreate(savedInstanceState)

        ColorPatchUtils.setTheme(this, billingViewModel.proStatus.value == true)
        UiUtils.isTabletUi = resources.getBoolean(R.bool.is_tablet_ui)

//        if (!BuildConfig.DEBUG)
        FragmentManager.enablePredictiveBack(false)

        binding = ContentMainBinding.inflate(layoutInflater)

        if (UiUtils.isTabletUi) {
            navHeaderbinding = HeaderNavBinding.inflate(layoutInflater, binding.sidebarNav, false)

            if (mainNotifierViewModel.isItChristmas)
                UiUtils.applySnowfall(
                    navHeaderbinding!!.navProfilePic,
                    navHeaderbinding!!.root,
                    layoutInflater,
                    lifecycleScope
                )

            mainFab = ExtendedFloatingActionButton(this).apply {
                id = R.id.main_extended_fab
                val lp = CoordinatorLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.sidebar_width) - 2 * resources.getDimensionPixelSize(
                        R.dimen.fab_margin
                    ),
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT
                )
                lp.insetEdge = Gravity.TOP
                lp.gravity = Gravity.TOP or Gravity.START
                lp.setMargins(resources.getDimensionPixelSize(R.dimen.fab_margin))
                layoutParams = lp
                visibility = View.GONE
                binding.root.addView(this)
            }
            binding.sidebarNav.addHeaderView(navHeaderbinding!!.root)
            binding.sidebarNav.visibility = View.VISIBLE
        } else {
            mainFab = FloatingActionButton(this).apply {
                id = R.id.main_fab
                val lp = CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT
                )
                lp.dodgeInsetEdges = Gravity.BOTTOM or Gravity.END
                lp.anchorGravity = Gravity.BOTTOM or Gravity.END
                lp.anchorId = R.id.nav_host_fragment
                setupInsets(
                    additionalSpaceBottom = 8.dp,
                    additionalSpaceSides = 16.dp,
                    addBottomNavHeight = false
                )
                visibility = View.INVISIBLE
                layoutParams = lp
                binding.root.addView(this)
            }
            binding.sidebarNav.visibility = View.GONE
        }

        if (Stuff.isTv) {
            binding.ctl.updateLayoutParams<AppBarLayout.LayoutParams> {
                scrollFlags =
                    scrollFlags or AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
            }
        }

        if (Stuff.isEdgeToEdge) {
            binding.root.fitsSystemWindows = true
            binding.appBar.fitsSystemWindows = true
            binding.heroDarkOverlayTop.isVisible = true
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        setContentView(binding.root)

        // make back button unfocusable on tv
        if (Stuff.isTv) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                binding.toolbar.children
                    .find { it is ImageButton }
                    ?.isFocusable = false
        }

        navController = binding.navHostFragment.getFragment<NavHostFragment>().navController

        if (Stuff.isLoggedIn()) {
            canShowNotices = true
            mainNotifierViewModel.initializeCurrentUser(Scrobblables.currentScrobblableUser!!)
            if (savedInstanceState == null && intent?.categories?.contains(Notification.INTENT_CATEGORY_NOTIFICATION_PREFERENCES) == true) {
                navController.navigate(R.id.prefFragment)
            }
        }

        val appBarConfiguration = AppBarConfiguration(navController.graph)
        binding.ctl.setupWithNavController(binding.toolbar, navController, appBarConfiguration)

        navHeaderbinding?.let {
            NavUtils.setProfileSwitcher(it, navController, mainNotifierViewModel)
        }

        navController.addOnDestinationChangedListener(this)

        collectLatestLifecycleFlow(mainNotifierViewModel.fabData) {
            // onDestroy of previous fragment gets called AFTER on create of the current fragment
            it ?: return@collectLatestLifecycleFlow

            it.lifecycleOwner.lifecycle.addObserver(
                object : LifecycleEventObserver {
                    override fun onStateChanged(
                        source: LifecycleOwner,
                        event: Lifecycle.Event
                    ) {
                        when (event) {
                            Lifecycle.Event.ON_DESTROY -> {
                                source.lifecycle.removeObserver(this)

                                if (mainNotifierViewModel.fabData.value?.lifecycleOwner == source)
                                    hideFab()
                            }

                            else -> {}
                        }
                    }
                }
            )

            if (UiUtils.isTabletUi) {
                (mainFab as ExtendedFloatingActionButton).apply {
                    setIconResource(it.iconRes)
                    setText(it.stringRes)
                }
            } else {
                (mainFab as FloatingActionButton).apply {
                    setImageResource(it.iconRes)
                    contentDescription = getString(it.stringRes)
                }
            }
            mainFab.setOnClickListener(it.clickListener)
            mainFab.setOnLongClickListener(it.longClickListener)
            showHiddenFab()
        }

        collectLatestLifecycleFlow(mainNotifierViewModel.canIndex) {
            if (!BuildConfig.DEBUG)
                binding.sidebarNav.menu.findItem(R.id.nav_do_index)?.isVisible = it
            if (it && prefs.lastMaxIndexTime != null) {
                IndexingWorker.schedule(this)
            }
        }

        collectLatestLifecycleFlow(mainNotifierViewModel.updateAvailable) { release ->
            Snackbar.make(
                binding.coordinator,
                getString(R.string.update_available, release.tag_name),
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(getString(R.string.changelog)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(release.tag_name)
                        .setMessage(release.body)
                        .setPositiveButton(R.string.download) { _, _ ->
                            release.downloadUrl?.let {
                                Stuff.openInBrowser(it)
                            }
                        }
                        .show()
                }
                .apply { if (!UiUtils.isTabletUi) anchorView = binding.bottomNav }
                .focusOnTv()
                .show()
        }

        collectLatestLifecycleFlow(billingViewModel.proStatus) {
            if (it) {
                binding.sidebarNav.menu.removeItem(R.id.nav_pro)
            }
        }
        billingViewModel.queryPurchases()

        if (canShowNotices) {
            lifecycleScope.launch {
                showSnackbarIfNeeded()
            }
        }

        collectLatestLifecycleFlow(mainNotifierViewModel.drawerData) {
            NavUtils.updateHeaderWithDrawerData(
                navHeaderbinding ?: return@collectLatestLifecycleFlow,
                mainNotifierViewModel
            )
        }

        collectLatestLifecycleFlow(App.globalExceptionFlow) { e ->
            if (BuildConfig.DEBUG)
                e.printStackTrace()

            if (e is FmException) {
                Snackbar.make(
                    binding.root,
                    e.localizedMessage ?: e.message,
                    Snackbar.LENGTH_SHORT
                ).apply { if (!UiUtils.isTabletUi) anchorView = binding.bottomNav }
                    .show()
            }
        }
//        navController.navigate(R.id.fixItFragment)
    }

    fun hideFab(removeListeners: Boolean = true) {
        if (UiUtils.isTabletUi) {
            (mainFab as ExtendedFloatingActionButton).hide()
            binding.sidebarNav.updateLayoutParams<MarginLayoutParams> {
                topMargin = 0
            }
        } else {
            (mainFab as FloatingActionButton).hide()
        }

        if (removeListeners) {
            mainNotifierViewModel.setFabData(null)
            mainFab.setOnClickListener(null)
        }
    }

    private fun showHiddenFab() {
        if (mainNotifierViewModel.fabData.value == null) return

        if (UiUtils.isTabletUi) {
            (mainFab as ExtendedFloatingActionButton).show()
            binding.sidebarNav.updateLayoutParams<MarginLayoutParams> {
                topMargin = resources.getDimensionPixelSize(R.dimen.fab_margin)
            }
        } else {
            (mainFab as FloatingActionButton).show()
        }
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        val showBottomNavOn = setOf(
            R.id.myHomePagerFragment,
            R.id.othersHomePagerFragment,
            R.id.chartsPagerFragment,
            R.id.infoExtraFullFragment
        )

        if (destination !is FloatingWindow && destination.id !in showBottomNavOn) {
            binding.appBar.expandTillToolbar()
        }

        fadeToolbarTitle(binding.ctl)

        destination.arguments[Stuff.ARG_TITLE]?.let {
            binding.ctl.title = it.defaultValue as String
        }

        mainNotifierViewModel.prevDestinationId = destination.id
    }

    private suspend fun showSnackbarIfNeeded() {
        delay(1500)
        val nlsEnabled = Stuff.isNotificationListenerEnabled()

        if (nlsEnabled && !Stuff.isScrobblerRunning()) {
            Snackbar.make(
                binding.root,
                R.string.not_running,
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(R.string.not_running_fix_action) {
                    navController.navigate(R.id.fixItFragment)
                }
                .apply { if (!UiUtils.isTabletUi) anchorView = binding.bottomNav }
                .focusOnTv()
                .show()
            Timber.tag(Stuff.TAG).w(Exception("${Stuff.SCROBBLER_PROCESS_NAME} not running"))
        } else if (!nlsEnabled || !prefs.scrobblerEnabled) {
            Snackbar.make(
                binding.root,
                R.string.scrobbler_off,
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(R.string.enable) {
                    if (!nlsEnabled)
                        navController.navigate(R.id.onboardingFragment)
                    else
                        prefs.scrobblerEnabled = true
                }
                .apply { if (!UiUtils.isTabletUi) anchorView = binding.bottomNav }
                .focusOnTv()
                .show()
        } else
            mainNotifierViewModel.checkForUpdatesIfNeeded()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        navController.handleDeepLink(intent)
        if (Stuff.isLoggedIn() && intent?.categories?.contains(Notification.INTENT_CATEGORY_NOTIFICATION_PREFERENCES) == true) {
            navController.navigate(R.id.prefFragment)
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase ?: return)
        setLocaleCompat()
    }

//    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
//        Stuff.log("focus: $currentFocus")
//        return super.onKeyUp(keyCode, event)
//    }

    // https://stackoverflow.com/a/28939113/1067596
// EditText, clear focus on touch outside
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    v.clearFocus()
                    ContextCompat.getSystemService(this, InputMethodManager::class.java)
                        ?.hideSoftInputFromWindow(v.getWindowToken(), 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
