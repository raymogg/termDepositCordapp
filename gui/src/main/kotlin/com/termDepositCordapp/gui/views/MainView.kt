package com.termDepositCordapp.gui.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.binding.Bindings
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.TextAlignment
import javafx.stage.Stage
import javafx.stage.WindowEvent
import net.corda.client.jfx.utils.ChosenList
import net.corda.client.jfx.utils.map
import com.termDepositCordapp.gui.formatters.PartyNameFormatter
import com.termDepositCordapp.gui.model.CordaViewModel
import tornadofx.*
import javafx.scene.control.*
import net.corda.client.jfx.model.*
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.x500Name
import net.corda.core.utilities.getOrThrow
import net.corda.core.messaging.startFlow
import java.time.LocalDateTime

/**
 * The root view embeds the [Shell] and provides support for the status bar, and modal dialogs.
 */
class MainView : View() {
    override val root by fxml<Parent>()

    // Inject components.
    private val userButton by fxid<MenuButton>()
    private val txnsButton by fxid<Button>()
    private val exit by fxid<MenuItem>()
    private val sidebar by fxid<VBox>()
    private val selectionBorderPane by fxid<BorderPane>()

    // Inject data.
    private val myIdentity by observableValue(NetworkIdentityModel::myIdentity)
    private val parties by observableList(NetworkIdentityModel::parties)
    private val selectedView by objectProperty(CordaViewModel::selectedView)
    private val registeredViews by observableList(CordaViewModel::registeredViews)
    private val rpcProxy by observableValue(NodeMonitorModel::proxyObservable)
    private val allNodes by observableList(NetworkIdentityModel::parties) //todo not sure if parties actually gets all nodes or not


    private val menuItemCSS = "sidebar-menu-item"
    private val menuItemArrowCSS = "sidebar-menu-item-arrow"
    private val menuItemSelectedCSS = "$menuItemCSS-selected"

    init {
        // Header
        userButton.textProperty().bind(myIdentity.map { it?.name?.let { PartyNameFormatter.short.format(it) } })
        exit.setOnAction {
            (root.scene.window as Stage).fireEvent(WindowEvent(root.scene.window, WindowEvent.WINDOW_CLOSE_REQUEST))
        }
        // Sidebar
        val menuItems = registeredViews.map {
            // This needed to be declared val or else it will get GCed and listener unregistered.
            val buttonStyle = ChosenList(selectedView.map { selected ->
                if (selected == it) listOf(menuItemCSS, menuItemSelectedCSS).observable() else listOf(menuItemCSS).observable()
            })
            stackpane {
                button(it.title) {
                    graphic = FontAwesomeIconView(it.icon).apply {
                        glyphSize = 30
                        textAlignment = TextAlignment.CENTER
                        fillProperty().bind(this@button.textFillProperty())
                    }
                    Bindings.bindContent(styleClass, buttonStyle)
                    setOnMouseClicked { e ->
                        if (e.button == MouseButton.PRIMARY) {
                            selectedView.value = it
                        }
                    }
                    // Transform to smaller icon layout when sidebar width is below 150.
                    val smallIconProperty = widthProperty().map { (it.toDouble() < 150) }
                    contentDisplayProperty().bind(smallIconProperty.map { if (it) ContentDisplay.TOP else ContentDisplay.LEFT })
                    textAlignmentProperty().bind(smallIconProperty.map { if (it) TextAlignment.CENTER else TextAlignment.LEFT })
                    alignmentProperty().bind(smallIconProperty.map { if (it) Pos.CENTER else Pos.CENTER_LEFT })
                    fontProperty().bind(smallIconProperty.map { if (it) Font.font(10.0) else Font.font(12.0) })
                    wrapTextProperty().bind(smallIconProperty)
                }
                // Small triangle indicator to make selected view more obvious.
                add(FontAwesomeIconView(FontAwesomeIcon.CARET_LEFT).apply {
                    StackPane.setAlignment(this, Pos.CENTER_RIGHT)
                    StackPane.setMargin(this, Insets(0.0, -5.0, 0.0, 0.0))
                    styleClass.add(menuItemArrowCSS)
                    visibleProperty().bind(selectedView.map { selected -> selected == it })
                })
            }
        }
        Bindings.bindContent(sidebar.children, menuItems)
        // Main view
        selectionBorderPane.centerProperty().bind(selectedView.map { it?.root })
    }

    fun runTxns() {
        //The following simulates specific trades and is accessed by clicking the txns button in the main view of the cordapp.
        val myInfo = rpcProxy.value!!.nodeInfo().legalIdentities.first()
        //Only option for finding an exact party to
        val bankA = parties.filter { it.legalIdentities.first().name == CordaX500Name("BankA", "Munich", "DE") }
        val bankB = parties.filter { it.legalIdentities.first().name == CordaX500Name("BankB", "Singapore", "SG") }

    }
}
