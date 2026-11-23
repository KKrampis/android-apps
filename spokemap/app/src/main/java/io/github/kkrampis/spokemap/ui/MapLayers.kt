package io.github.kkrampis.spokemap.ui

import io.github.kkrampis.spokemap.data.Kind
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource

/** Sources and layers the app draws on top of the base map. */
object MapLayers {
    const val SRC_OSM = "spoke-osm"
    const val SRC_NYC = "spoke-nyc"
    const val SRC_NJ = "spoke-nj"
    const val SRC_BIKES = "spoke-bikes"

    const val L_OSM = "spoke-osm-lines"
    const val L_NYC = "spoke-nyc-lines"
    const val L_NJ = "spoke-nj-lines"
    const val L_BIKES = "spoke-bikes-circles"
    const val L_BIKES_LABEL = "spoke-bikes-labels"

    val LANE_LAYERS = arrayOf(L_OSM, L_NYC, L_NJ)

    fun install(style: Style) {
        for (id in listOf(SRC_NJ, SRC_NYC, SRC_OSM, SRC_BIKES)) {
            if (style.getSource(id) == null) style.addSource(GeoJsonSource(id))
        }

        // Bottom to top: NJ stress (wide and translucent), NYC DOT, OpenStreetMap, Citi Bike.
        style.addLayer(
            LineLayer(L_NJ, SRC_NJ).withProperties(
                PropertyFactory.lineColor(
                    match(
                        get("los"), color(Palette.STRESS_UNKNOWN),
                        stop(1, color(Palette.STRESS[0])),
                        stop(2, color(Palette.STRESS[1])),
                        stop(3, color(Palette.STRESS[2])),
                        stop(4, color(Palette.STRESS[3])),
                        stop(5, color(Palette.STRESS[4])),
                    ),
                ),
                PropertyFactory.lineWidth(widthBy(2f, 4f, 9f)),
                PropertyFactory.lineOpacity(0.55f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addLayer(
            LineLayer(L_NYC, SRC_NYC).withProperties(
                PropertyFactory.lineColor(kindColor()),
                PropertyFactory.lineWidth(widthBy(1.2f, 2.5f, 5f)),
                PropertyFactory.lineDasharray(arrayOf(2f, 1f)),
                PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addLayer(
            LineLayer(L_OSM, SRC_OSM).withProperties(
                PropertyFactory.lineColor(kindColor()),
                PropertyFactory.lineWidth(widthBy(1.2f, 2.5f, 5f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )

        val bikeColor = Expression.switchCase(
            Expression.eq(get("renting"), literal(false)), color(Palette.STATION_CLOSED),
            step(
                get("bikes"), color(Palette.STATION_EMPTY),
                stop(1, color(Palette.STATION_FEW)),
                stop(4, color(Palette.STATION_OK)),
            ),
        )
        style.addLayer(
            CircleLayer(L_BIKES, SRC_BIKES).withProperties(
                PropertyFactory.circleColor(bikeColor),
                PropertyFactory.circleRadius(
                    interpolate(linear(), zoom(), stop(10, 2.5f), stop(13, 5f), stop(15, 9f), stop(17, 13f)),
                ),
                PropertyFactory.circleStrokeColor(Palette.STATION_STROKE),
                PropertyFactory.circleStrokeWidth(1.2f),
            ),
        )
        style.addLayer(
            SymbolLayer(L_BIKES_LABEL, SRC_BIKES).withProperties(
                PropertyFactory.textField(Expression.toString(get("bikes"))),
                PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                PropertyFactory.textSize(interpolate(linear(), zoom(), stop(14.5, 9f), stop(17, 13f))),
                PropertyFactory.textColor(Palette.STATION_STROKE),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            ).also { it.minZoom = 14.5f },
        )
    }

    fun setVisible(style: Style, layers: Layers) {
        fun vis(id: String, on: Boolean) {
            style.getLayer(id)?.setProperties(PropertyFactory.visibility(if (on) Property.VISIBLE else Property.NONE))
        }
        vis(L_OSM, layers.osm)
        vis(L_NYC, layers.nyc)
        vis(L_NJ, layers.nj)
        vis(L_BIKES, layers.bikes)
        vis(L_BIKES_LABEL, layers.bikes)
    }

    fun setData(style: Style, sourceId: String, json: String) {
        style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(json)
    }

    private fun kindColor(): Expression = match(
        get("kind"), color(Palette.KIND_OTHER),
        stop(Kind.PATH, color(Palette.KIND_PATH)),
        stop(Kind.PROTECTED, color(Palette.KIND_PROTECTED)),
        stop(Kind.LANE, color(Palette.KIND_LANE)),
        stop(Kind.SHARED, color(Palette.KIND_SHARED)),
    )

    private fun widthBy(z11: Float, z14: Float, z17: Float): Expression =
        interpolate(linear(), zoom(), stop(11, z11), stop(14, z14), stop(17, z17))
}
