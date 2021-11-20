package MyPaint

import javafx.application.Application
import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.scene.Scene
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.event.EventHandler
import javafx.geometry.Rectangle2D
import javafx.scene.SnapshotParameters
import javafx.scene.canvas.Canvas
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.MouseButton
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.scene.shape.StrokeLineJoin
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.File
import javax.imageio.ImageIO

class Main : Application() {
    private lateinit var primaryStage: Stage
    private var configFile = getConfigFile()
    private lateinit var paintLayout: AnchorPane
    private var image = ImageView()


    override fun start(primaryStage: Stage) {
        this.primaryStage = primaryStage
        val scene = screenshotScene()

        primaryStage.title = "Paint"
        primaryStage.scene = scene
        primaryStage.show()
    }

    private fun screenshotScene(): Scene {
        val rootScreenshot = VBox()
        val menuBar = setMenuBarScreenshot()

        val slider = Slider(0.0, 10.0, 0.0)
        slider.blockIncrement = 1.0
        slider.majorTickUnit = 1.0
        slider.minorTickCount = 0
        slider.isShowTickLabels = true
        slider.isSnapToTicks = true

        val needRollUp = CheckBox("Свернуть")
        val takeScreenshotBtn = Button("Сделать скриншот")

        rootScreenshot.children.addAll(menuBar, takeScreenshotBtn, slider, needRollUp)

        val scene = Scene(rootScreenshot, 500.0, 120.0)

        takeScreenshotBtn.onAction = EventHandler {
            if (needRollUp.isSelected)
                primaryStage.isIconified = true

            Thread.sleep(
                if (slider.value.toInt() != 0)
                    1000 * slider.value.toLong()
                else 200
            )
            val img = SwingFXUtils.toFXImage(
                Robot().createScreenCapture(Rectangle(Toolkit.getDefaultToolkit().screenSize)),
                null
            )
            primaryStage.scene = paintScene(img)
            primaryStage.isMaximized = true

        }
        return scene
    }

    private fun paintScene(img: WritableImage): Scene {
        val rootScroll = ScrollPane()

        val rootMain = VBox()
        paintLayout = AnchorPane()
        val toolLayout = HBox()
        image.image = img
        val menu = setMenuBarPaint()
        val colorPicker = ColorPicker(Color.BLACK)
        val colorSize = Slider(1.0, 20.0, 1.0)
        val cropCheckBox = CheckBox("Обрезка")

        var cropEndX: Double
        var cropEndY: Double
        var cropStartX = 0.0
        var cropStartY = 0.0

        val coeff = 0.85
        val width = Toolkit.getDefaultToolkit().screenSize.getWidth() * coeff
        val height = Toolkit.getDefaultToolkit().screenSize.getHeight() * coeff
        val canvas = Canvas(width, height)
        image.fitWidth = width
        image.fitHeight = height

        val g = canvas.graphicsContext2D
        g.globalAlpha = 0.7
        g.lineJoin = StrokeLineJoin.ROUND

        canvas.onMousePressed = EventHandler { e ->
            if (cropCheckBox.isSelected) {
                cropStartX = e.x
                cropStartY = e.y
            } else {
                g.lineWidth = colorSize.value
                g.stroke = colorPicker.value
                g.beginPath()
            }
        }
        canvas.onMouseDragged = EventHandler { e ->
            if (cropCheckBox.isSelected) {
                // pass
            } else {
                val size = colorSize.value
                val x = e.x - size / 2
                val y = e.y - size / 2
                if (e.button == MouseButton.SECONDARY) {
                    g.clearRect(x, y, size, size)
                } else {
                    g.lineTo(e.x, e.y)
                    g.stroke()
                }
            }
        }
        canvas.onMouseReleased = EventHandler { e ->
            if (cropCheckBox.isSelected) {
                cropEndX = e.x
                cropEndY = e.y

                var leftX = minOf(cropStartX, cropEndX)
                var rightX = maxOf(cropStartX, cropEndX)
                var leftY = minOf(cropStartY, cropEndY)
                var rightY = maxOf(cropStartY, cropEndY)

                if (leftX < 0) leftX = 0.0
                if (leftY < 0) leftY = 0.0
                if (rightX > image.fitWidth) rightX = image.fitWidth
                if (rightY > image.fitHeight) rightY = image.fitHeight

                val ssp = SnapshotParameters()
                ssp.viewport = Rectangle2D(
                    leftX,
                    leftY,
                    rightX - leftX,
                    rightY - leftY
                )
                val cropImg: WritableImage = image.snapshot(ssp, null)
                image.fitWidth = cropImg.width
                image.fitHeight = cropImg.height
                image.image = cropImg

                canvas.translateX = -leftX
                canvas.translateY = -leftY
                canvas.width = rightX
                canvas.height = rightY
            } else {
                g.closePath()
            }
        }

        rootScroll.content = paintLayout
        rootScroll.maxWidth = width + 2
        toolLayout.children.addAll(colorPicker, colorSize, cropCheckBox)
        rootMain.children.addAll(menu, toolLayout, rootScroll)
        paintLayout.children.addAll(image, canvas)

        val scene = Scene(rootMain)

        val saveKey = KeyCodeCombination(KeyCode.S, KeyCodeCombination.CONTROL_DOWN, KeyCodeCombination.SHIFT_DOWN)
        val fastSaveKey = KeyCodeCombination(KeyCode.S, KeyCodeCombination.CONTROL_DOWN)
        val newScreenshotKey = KeyCodeCombination(KeyCode.N, KeyCodeCombination.CONTROL_DOWN)

        scene.accelerators[saveKey] = Runnable { saveAs() }
        scene.accelerators[fastSaveKey] = Runnable { fastSave() }
        scene.accelerators[newScreenshotKey] = Runnable {
            primaryStage.isMaximized = false
            primaryStage.scene = screenshotScene()
        }

        return scene
    }

    private fun setMenuBarScreenshot(): MenuBar {
        val menuBar = MenuBar()

        val menu = Menu("Файл")

        val open = MenuItem("Открыть")
        val close = MenuItem("Закрыть")

        open.onAction = EventHandler {
            open()
        }
        close.onAction = EventHandler {
            Platform.exit()
        }

        menu.items.addAll(open, close)

        menuBar.menus.add(menu)
        return menuBar
    }

    private fun setMenuBarPaint(): MenuBar {
        val menuBar = MenuBar()

        val menuFile = Menu("Файл")

        val newScreenshot = MenuItem("Новый снимок экрана, ctrl + n")
        val open = MenuItem("Открыть")
        val save = MenuItem("Сохранить, ctrl + shift + s")
        val fastSave = MenuItem("Быстрое сохранение, ctrl + s")
        val close = MenuItem("Закрыть")

        newScreenshot.onAction = EventHandler {
            primaryStage.isMaximized = false
            primaryStage.scene = screenshotScene()
        }
        open.onAction = EventHandler {
            open()
        }
        save.onAction = EventHandler {
            saveAs()
        }
        fastSave.onAction = EventHandler {
            fastSave()
        }
        close.onAction = EventHandler {
            Platform.exit()
        }

        menuFile.items.addAll(newScreenshot, open, save, fastSave, close)

        menuBar.menus.addAll(menuFile)
        return menuBar
    }

    private fun open() {
        val fileChooser = FileChooser()
        fileChooser.extensionFilters.addAll(
            FileChooser.ExtensionFilter("All Files", "*.*"),
            FileChooser.ExtensionFilter("JPG", "*.jpg", "*.jpeg"),
            FileChooser.ExtensionFilter("PNG", "*.png"),
        )
        val file = fileChooser.showOpenDialog(primaryStage) ?: return
        try {
            val img = SwingFXUtils.toFXImage(ImageIO.read(file), null)
            primaryStage.scene = paintScene(img)
        } catch (e: Exception) {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.contentText = e.toString()
            alert.show()
        }
        primaryStage.isMaximized = false
        primaryStage.isMaximized = true
    }

    private fun getFileForSave(dir: String): File {
        var file = File("$dir\\image.png")
        var num = 1
        while (file.exists()) {
            file = File("$dir\\image ($num).png")
            num++
        }
        return file
    }

    private fun getConfigFile(): File {
        val path = System.getenv("LOCALAPPDATA")
            ?: System.getenv("XDG_DATA_HOME")
            ?: "./"
        return File("$path\\myScreenshotAndPaint.conf")
    }

    private fun save(dir: File) {
        val snapshot = SnapshotParameters()
//        println(paintLayout.translateX)
        snapshot.viewport = Rectangle2D(
            paintLayout.layoutX,
            paintLayout.layoutY,
            image.fitWidth,
            image.fitHeight
        )
        val image = paintLayout.snapshot(snapshot, null)

        val file = getFileForSave(dir.toString())
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file)
        } catch (e: Exception) {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.contentText = e.toString()
            alert.showAndWait()
            return
        }
        val alert = Alert(Alert.AlertType.INFORMATION)
        alert.contentText = "Сохранение успешно"
        alert.show()
    }

    private fun saveAs() {
        val directoryChooser = DirectoryChooser()
        try {
            directoryChooser.initialDirectory = File(configFile.readText())
        } catch (e: Exception) {
        }
        val dir = directoryChooser.showDialog(primaryStage) ?: return

        save(dir)
        configFile.writeText(dir.toString())
    }

    private fun fastSave() {
        val dir = System.getenv("HOME")
            ?: System.getenv("USERPROFILE")?.plus("\\Desktop")
            ?: "./"
        save(File(dir))
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(Main::class.java)
        }
    }
}
