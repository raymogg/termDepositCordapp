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
import com.termDeposits.contract.KYC
import com.termDeposits.contract.TermDepositOffer
import javafx.geometry.Pos
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import tornadofx.*
import java.time.LocalDateTime


class KYCViewer : CordaView("KYC Data") {
    // Inject UI elements.
    override val root: BorderPane by fxml()
    override val icon: FontAwesomeIcon = FontAwesomeIcon.ADDRESS_CARD
    override val widgets = listOf(CordaWidget("KYC Data", OfferWidget(), icon)).observable()
    // Left pane
    private val leftPane: VBox by fxid()
    private val splitPane: SplitPane by fxid()
    private val totalMatchingLabel: Label by fxid()
    private val claimViewerTable: TreeTableView<ViewerNode> by fxid()
    private val claimViewerTableExchangeHolding: TreeTableColumn<ViewerNode, String> by fxid()
    // Right pane
    private val rightPane: VBox by fxid()
    private val totalPositionsLabel: Label by fxid()
    private val claimStatesList: ListView<StateRow> by fxid()
    private val toggleButton by fxid<Button>()

    // Inject observables
    private val claimStates by observableList(TermDepositsModel::KYCStates)

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
    sealed class ViewerNode(val states: ObservableList<StateAndRef<KYC.State>>) {
        class ExchangeNode(val uniqueID: String,
                           states: ObservableList<StateAndRef<KYC.State>>) : ViewerNode(states)

    }
    /**
     * Holds data for a single state, to be displayed in the list in the side pane.
     */
    data class StateRow(val originated: LocalDateTime, val stateAndRef: StateAndRef<KYC.State>)

    /**
     * A small class describing the graphics of a single state.
     */
    inner class StateRowGraphic(val stateRow: StateRow) : UIComponent() {
        override val root: Parent by fxml("KYCStateViewer.fxml")

        val stateIdValueLabel: Label by fxid()
        val clientNameValueLabel: Label by fxid()
        val accountNumValueLabel: Label by fxid()
        val clientIDValueLabel: Label by fxid()


        init {
            stateIdValueLabel.apply {
                text = stateRow.stateAndRef.ref.toString().substring(0, 16) + "...[${stateRow.stateAndRef.ref.index}]"
                graphic = identicon(stateRow.stateAndRef.ref.txhash, 30.0)
                tooltip = identiconToolTip(stateRow.stateAndRef.ref.txhash)
            }

            clientNameValueLabel.text = stateRow.stateAndRef.state.data.firstName + " " + stateRow.stateAndRef.state.data.lastName
            accountNumValueLabel.text = stateRow.stateAndRef.state.data.accountNum
            clientIDValueLabel.text = stateRow.stateAndRef.state.data.linearId.toString()
        }
    }

    // Wire up UI
    init {
        Bindings.bindContent(splitPane.items, view)

        val searchField = SearchField(claimStates,
                "Name" to { state, text -> (state.state.data.firstName.toString().contains(text, true) || state.state.data.lastName.contains(text, true)) },
                "Client ID" to {state, text -> state.state.data.linearId.toString().contains(text, true)}
        )
        root.top = hbox(5.0) {
            button("Create Client KYC", FontAwesomeIconView(FontAwesomeIcon.PLUS)) {
                setOnMouseClicked {
                    if (it.button == MouseButton.PRIMARY) {
                        find<CreateKYC>().show(this@KYCViewer.root.scene.window)

                    }
                }
            }
            button("Update Client KYC", FontAwesomeIconView(FontAwesomeIcon.PLUS)) {
                setOnMouseClicked {
                    if (it.button == MouseButton.PRIMARY) {
                        find<UpdateKYC>().show(this@KYCViewer.root.scene.window)

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
                AggregatedList(searchField.filteredData, { "${it.state.data.firstName} ${it.state.data.lastName}" }) { exchange, memberStates ->
                    /**
                     * Next we create subgroups based on holding. [memberStates] here is all states holding stock [stock] issued by [exchange] above.
                     * Note that these states will not be displayed in the TreeTable, but rather in the side pane if the user clicks on the row.
                     */
                    val stockNodes = AggregatedList(memberStates, { it.state.data.linearId }) { stock, memberStates ->

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
//                        val children: List<TreeItem<out ViewerNode.ExchangeNode>> = treeItem.children
//                        Bindings.bindContent(children, stockNodes)
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
                is ViewerNode.ExchangeNode -> SimpleStringProperty(node.uniqueID.toString())
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

    //This is the widget that is diplayed in the dashboard view on the homepage
    private class OfferWidget : BorderPane() {
        //private val partiallyResolvedTransactions by observableListReadOnly(TransactionDataModel::partiallyResolvedTransactions)
        private val claimStates by observableList(TermDepositsModel::KYCStates)

        init {
            right {
                label {
                    val hash = SecureHash.randomSHA256()
                    graphic = identicon(hash, 30.0)
                    textProperty().bind(Bindings.size(claimStates).map(Number::toString))
                    BorderPane.setAlignment(this, Pos.BOTTOM_RIGHT)
                }
            }


        }
    }

}
