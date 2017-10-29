package com.supercilex.robotscouter.ui.teamlist

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.util.ViewPreloadSizeProvider
import com.firebase.ui.common.ChangeEventType
import com.firebase.ui.firestore.FirestoreRecyclerAdapter
import com.firebase.ui.firestore.FirestoreRecyclerOptions
import com.firebase.ui.firestore.ObservableSnapshotArray
import com.google.firebase.firestore.DocumentSnapshot
import com.supercilex.robotscouter.R
import com.supercilex.robotscouter.data.model.Team
import com.supercilex.robotscouter.util.ui.CardListHelper
import org.jetbrains.anko.support.v4.find
import java.util.Collections

class TeamListAdapter(snapshots: ObservableSnapshotArray<Team>,
                      private val fragment: Fragment,
                      private val menuManager: TeamMenuHelper,
                      private val selectedTeamIdListener: MutableLiveData<Team?>) :
        FirestoreRecyclerAdapter<Team, TeamViewHolder>(
                FirestoreRecyclerOptions.Builder<Team>()
                        .setSnapshotArray(snapshots)
                        .setLifecycleOwner(fragment)
                        .build()),
        ListPreloader.PreloadModelProvider<Team>, Observer<Team?> {
    private val viewSizeProvider = ViewPreloadSizeProvider<Team>()
    private val preloader = RecyclerViewPreloader<Team>(
            Glide.with(fragment),
            this,
            viewSizeProvider,
            5)

    private val recyclerView = fragment.find<RecyclerView>(R.id.list)
    private val cardListHelper = CardListHelper(this, recyclerView)
    private val noTeamsHint: View = fragment.find(R.id.no_content_hint)

    private var selectedTeamId: String? = null

    init {
        recyclerView.addOnScrollListener(preloader)
        selectedTeamIdListener.observeForever(this)
    }

    override fun startListening() {
        super.startListening()

        // More annoying constructor bugs: this will be called before we can assign our fields,
        // thus the NPE
        @Suppress("UNNECESSARY_SAFE_CALL")
        selectedTeamIdListener?.observeForever(this)
    }

    override fun onChanged(team: Team?) {
        val oldId = selectedTeamId
        selectedTeamId = team?.id.let {
            if (TextUtils.equals(selectedTeamId, it)) return else it
        }

        var hasScrolledToPos = false
        val visiblePositions = (recyclerView.layoutManager as LinearLayoutManager).let {
            it.findFirstCompletelyVisibleItemPosition()..it.findLastCompletelyVisibleItemPosition()
        }

        for (i in 0 until itemCount) {
            val id = getItem(i).id
            if (TextUtils.equals(id, selectedTeamId)) {
                if (!hasScrolledToPos) {
                    if (i !in visiblePositions) recyclerView.smoothScrollToPosition(i)
                    hasScrolledToPos = true
                }

                notifyItemChanged(i)
            } else if (TextUtils.equals(id, oldId)) {
                notifyItemChanged(i)
            }
        }

        if (!hasScrolledToPos && !TextUtils.isEmpty(selectedTeamId)) {
            for (i in 0 until itemCount) {
                if (team != null && getItem(i).number >= team.number) {
                    if (i !in visiblePositions) recyclerView.smoothScrollToPosition(i)
                    return
                }
            }
            recyclerView.smoothScrollToPosition(itemCount)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamViewHolder =
            TeamViewHolder(LayoutInflater.from(parent.context)
                                   .inflate(R.layout.team_list_row_layout, parent, false)).also {
                viewSizeProvider.setView(it.mediaImageView)
            }

    override fun onBindViewHolder(teamHolder: TeamViewHolder, position: Int, team: Team) {
        cardListHelper.onBind(teamHolder)
        teamHolder.bind(
                team,
                fragment,
                recyclerView,
                menuManager,
                isTeamSelected(team),
                menuManager.areTeamsSelected(),
                TextUtils.equals(selectedTeamId, team.id))
    }

    private fun isTeamSelected(team: Team) = menuManager.selectedTeams.contains(team)

    override fun getPreloadRequestBuilder(team: Team): RequestBuilder<*> =
            TeamViewHolder.getTeamMediaRequestBuilder(
                    isTeamSelected(team), fragment.context!!, team)

    override fun getPreloadItems(position: Int): List<Team> =
            Collections.singletonList(getItem(position))

    override fun onChildChanged(type: ChangeEventType,
                                snapshot: DocumentSnapshot,
                                newIndex: Int,
                                oldIndex: Int) {
        super.onChildChanged(type, snapshot, newIndex, oldIndex)
        cardListHelper.onChildChanged(type, newIndex)
        if (type == ChangeEventType.CHANGED) {
            for (oldTeam in menuManager.selectedTeams) {
                val team = getItem(newIndex)
                if (TextUtils.equals(oldTeam.id, team.id)) {
                    menuManager.onSelectedTeamChanged(oldTeam, team)
                    break
                }
            }
        } else if (type == ChangeEventType.REMOVED && menuManager.areTeamsSelected()) {
            for (oldTeam in menuManager.selectedTeams) {
                if (snapshots.firstOrNull { it.id == oldTeam.id } == null) { // We found the deleted item
                    menuManager.onSelectedTeamRemoved(oldTeam)
                    break
                }
            }
        }
    }

    override fun onDataChanged() {
        noTeamsHint.visibility = if (itemCount == 0) View.VISIBLE else View.GONE
    }

    override fun stopListening() {
        super.stopListening()
        onDataChanged()
        recyclerView.removeOnScrollListener(preloader)
        selectedTeamIdListener.removeObserver(this)
    }
}
