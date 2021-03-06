package com.termDepositCordapp.gui.views.cordapps.termDeposits

import javafx.scene.layout.BorderPane
import net.corda.client.jfx.model.NetworkIdentityModel
import net.corda.client.jfx.model.NodeMonitorModel
import net.corda.client.jfx.model.observableList
import net.corda.client.jfx.model.observableValue
import com.sun.javafx.collections.ObservableListWrapper
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import net.corda.client.jfx.utils.*
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import com.termDepositCordapp.gui.formatters.AmountFormatter
import com.termDepositCordapp.gui.formatters.PartyNameFormatter
import com.termDepositCordapp.gui.identicon.identicon
import com.termDepositCordapp.gui.identicon.identiconToolTip
import com.termDepositCordapp.gui.model.*
import com.termDepositCordapp.gui.ui.*
import com.termDepositCordapp.gui.views.*
import com.termDeposits.contract.TermDeposit
import javafx.geometry.Pos
import net.corda.core.crypto.SecureHash
import tornadofx.*
import java.time.LocalDateTime


class MaturedViewer : CordaView("Matured Term Deposits") {
    // Inject UI elements.
    override val root: BorderPane by fxml()
    override val icon: FontAwesomeIcon = FontAwesomeIcon.ADDRESS_CARD
    override val widgets = listOf(CordaWidget("Matured Term Deposits", MaturedWidget(), icon)).observable()
    // Left pane
    private val leftPane: VBox by fxid()
    private val splitPane: SplitPane by fxid()
    private val totalMatchingLabel: Label by fxid()
    private val claimViewerTable: TreeTableView<ViewerNode> by fxid()
    private val claimViewerTableExchangeHolding: TreeTableColumn<ViewerNode, String> by fxid()
    private val claimViewerTableQuantity: TreeTableColumn<ViewerNode, Int> by fxid()
    // Right pane
    private val rightPane: VBox by fxid()
    private val totalPositionsLabel: Label by fxid()
    private val claimStatesList: ListView<StateRow> by fxid()
    private val toggleButton by fxid<Button>()

    // Inject observables
    private val claimStates by observableList(TermDepositsModel::maturedStates)


    private val selectedNode = claimViewerTable.singleRowSelection().map {
        when (it) {
            is SingleRowSelection.Selected -> it.node
            else -> null
        }
    }

    private val view = ChosenList(selectedNode.map {
        when (it) {
            null -> FXCollections.observableArrayList(leftPane)
            else -> FXCollections.observableArrayList(leftPane, rightPane)
        }
    })

    /**
     * This holds the data for each row in the TreeTable.
     */
    sealed class ViewerNode(val states: ObservableList<StateAndRef<TermDeposit.State>>) {
        class ExchangeNode(val exchange: AbstractParty,
                           states: ObservableList<StateAndRef<TermDeposit.State>>) : ViewerNode(states)

    }
    /**
     * Holds data for a single state, to be displayed in the list in the side pane.
     */
    data class StateRow(val originated: LocalDateTime, val stateAndRef: StateAndRef<TermDeposit.State>)

    /**
     * A small class describing the graphics of a single state.
     */
    inner class StateRowGraphic(val stateRow: StateRow) : UIComponent() {
        override val root: Parent by fxml("TermDepositStateViewer.fxml")

        val stateIdValueLabel: Label by fxid()
        val instituteValueLabel: Label by fxid()
        val interestValueLabel: Label by fxid()
        val depositValueLabel: Label by fxid()
        val startDateValueLabel: Label by fxid()
        val endDateValueLabel: Label by fxid()
        val internalStateValueLabel: Label by fxid()
        val clientRefValueLabel: Label by fxid()

        init {
            val resolvedIssuer: AbstractParty = stateRow.stateAndRef.state.data.institute

            stateIdValueLabel.apply {
                text = stateRow.stateAndRef.ref.toString().substring(0, 16) + "...[${stateRow.stateAndRef.ref.index}]"
                graphic = identicon(stateRow.stateAndRef.ref.txhash, 30.0)
                tooltip = identiconToolTip(stateRow.stateAndRef.ref.txhash)
            }
            instituteValueLabel.textProperty().bind(SimpleStringProperty(resolvedIssuer.nameOrNull()?.let {
                PartyNameFormatter.short.format(it)
            } ?: "Anonymous"))
            instituteValueLabel.apply { tooltip(resolvedIssuer.nameOrNull()?.let { PartyNameFormatter.full.format(it) } ?: "Anonymous") }
            interestValueLabel.text = stateRow.stateAndRef.state.data.interestPercent.toString()
            depositValueLabel.text = stateRow.stateAndRef.state.data.depositAmount.toString()
            startDateValueLabel.text = stateRow.stateAndRef.state.data.startDate.toString()
            endDateValueLabel.text = stateRow.stateAndRef.state.data.endDate.toString()
            internalStateValueLabel.text = stateRow.stateAndRef.state.data.internalState.toString()
            clientRefValueLabel.text = stateRow.stateAndRef.state.data.clientIdentifier.toString()

        }
    }

    // Wire up UI
    init {
        Bindings.bindContent(splitPane.items, view)

        val searchField = SearchField(claimStates,
                "Interest" to { state, text -> state.state.data.interestPercent.toString().contains(text, true) },
                "Client ID" to {state, text -> state.state.data.clientIdentifier.toString().contains(text, true)},
                "Maturity Date" to {state, text -> state.state.data.endDate.toString().contains(text, true)}
        )
        root.top = hbox(5.0) {

            button("Exit Deposit", FontAwesomeIconView(FontAwesomeIcon.PLUS)) {
                setOnMouseClicked {
                    if (it.button == MouseButton.PRIMARY) {
                        find<ExitDeposit>().show(this@MaturedViewer.root.scene.window)

                    }
                }
            }

            button("Rollover Deposit", FontAwesomeIconView(FontAwesomeIcon.PLUS)) {
                setOnMouseClicked {
                    if (it.button == MouseButton.PRIMARY) {
                        find<RolloverDeposit>().show(this@MaturedViewer.root.scene.window)

                    }
                }
            }
            HBox.setHgrow(searchField.root, Priority.ALWAYS)
            add(searchField.root)
        }

        /**
         * This is where we aggregate the list of states into the TreeTable structure.
         */
        val claimViewerExchangeNodes: ObservableList<TreeItem<out ViewerNode.ExchangeNode>> =
                /**
                 * First we group the states based on the exchange. [memberStates] is all states holding stock issued by [exchange]
                 */
                AggregatedList(searchField.filteredData, { it.state.data.institute }) { exchange, memberStates ->
                    /**
                     * Next we create subgroups based on holding. [memberStates] here is all states holding stock [stock] issued by [exchange] above.
                     * Note that these states will not be displayed in the TreeTable, but rather in the side pane if the user clicks on the row.
                     */
                    val stockNodes = AggregatedList(memberStates, { it.state.data.interestPercent }) { stock, memberStates ->

                    }

//                        /**
//                         * Assemble the Exchange node.
//                         */
                    val treeItem = TreeItem(ViewerNode.ExchangeNode(exchange, memberStates))
//
//                        /**
//                         * Bind the children in the TreeTable structure.
//                         *
//                         * TODO Perhaps we shouldn't do this here, but rather have a generic way of binding nodes to the treetable once.
//                         */
                    treeItem.isExpanded = true

                    treeItem
                }

        claimViewerTable.apply {
            root = TreeItem()
            val children: List<TreeItem<out ViewerNode>> = root.children
            Bindings.bindContent(children, claimViewerExchangeNodes)
            root.isExpanded = true
            isShowRoot = false
            // TODO use smart resize
            setColumnPrefWidthPolicy { tableWidthWithoutPaddingAndBorder, _ ->
                Math.floor(tableWidthWithoutPaddingAndBorder.toDouble() / columns.size).toInt()
            }
        }
        val quantityCellFactory = AmountFormatter.intFormatter.toTreeTableCellFactory<ViewerNode, Int>()

        claimViewerTableExchangeHolding.setCellValueFactory {
            val node = it.value.value
            when (node) {
            // TODO: Anonymous should probably be italicised or similar
                is ViewerNode.ExchangeNode -> SimpleStringProperty(node.exchange.let { PartyNameFormatter.short.format(it.nameOrNull()!!) } ?: "Anonymous")
            }
        }

        // Right Pane.
        totalPositionsLabel.textProperty().bind(claimStatesList.itemsProperty().map {
            val plural = if (it.size == 1) "" else "s"
            "Total ${it.size} position$plural"
        })

        claimStatesList.apply {
            // TODO update this once we have actual timestamps.
            itemsProperty().bind(selectedNode.map { it?.states?.map { StateRow(LocalDateTime.now(), it) } ?: ObservableListWrapper(emptyList()) })
            setCustomCellFactory { StateRowGraphic(it).root }
        }

        // TODO Think about i18n!
        totalMatchingLabel.textProperty().bind(Bindings.size(claimViewerExchangeNodes).map {
            val plural = if (it == 1) "" else "s"
            "Total $it matching issuer$plural"
        })

        toggleButton.setOnAction {
            claimViewerTable.selectionModel.clearSelection()
        }
    }

    private class MaturedWidget : BorderPane() {
        //private val partiallyResolvedTransactions by observableListReadOnly(TransactionDataModel::partiallyResolvedTransactions)
        private val claimStates by observableList(TermDepositsModel::maturedStates)
        init {
            right {
                label {
                    val hash = SecureHash.randomSHA256()
                    graphic = identicon(hash, 30.0)
                    var totalStock = 0;
                    textProperty().bind(Bindings.size(claimStates).map(Number::toString))
                    //textProperty().bind(Bindings.concat(totalStock))
                    BorderPane.setAlignment(this, Pos.BOTTOM_RIGHT)
                }
            }


        }
    }

}
