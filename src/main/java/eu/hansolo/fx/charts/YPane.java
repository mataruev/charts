package eu.hansolo.fx.charts;

import eu.hansolo.fx.charts.data.YData;
import eu.hansolo.fx.charts.font.Fonts;
import eu.hansolo.fx.charts.series.YSeries;
import eu.hansolo.fx.charts.tools.Point;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.DoublePropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class YPane<T extends YData> extends Region implements ChartArea {
    private static final double                PREFERRED_WIDTH  = 250;
    private static final double                PREFERRED_HEIGHT = 250;
    private static final double                MINIMUM_WIDTH    = 0;
    private static final double                MINIMUM_HEIGHT   = 0;
    private static final double                MAXIMUM_WIDTH    = 4096;
    private static final double                MAXIMUM_HEIGHT   = 4096;
    private static       double                aspectRatio;
    private              boolean               keepAspect;
    private              double                size;
    private              double                width;
    private              double                height;
    private              Pane                  pane;
    private              Paint                 _chartBackgroundPaint;
    private              ObjectProperty<Paint> chartBackgroundPaint;
    private              List<YSeries<T>>      listOfSeries;
    private              Canvas                canvas;
    private              GraphicsContext       ctx;
    private              double                _rangeY;
    private              DoubleProperty        rangeY;


    // ******************** Constructors **************************************
    public YPane(final YSeries<T>... SERIES) {
        this(Color.WHITE, SERIES);
    }
    public YPane(final Paint BACKGROUND, final YSeries<T>... SERIES) {
        getStylesheets().add(YPane.class.getResource("chart.css").toExternalForm());
        aspectRatio           = PREFERRED_HEIGHT / PREFERRED_WIDTH;
        keepAspect            = false;
        _chartBackgroundPaint = BACKGROUND;
        listOfSeries          = FXCollections.observableArrayList(SERIES);
        _rangeY               = 100;

        initGraphics();
        registerListeners();
    }


    // ******************** Initialization ************************************
    private void initGraphics() {
        if (Double.compare(getPrefWidth(), 0.0) <= 0 || Double.compare(getPrefHeight(), 0.0) <= 0 || Double.compare(getWidth(), 0.0) <= 0 ||
            Double.compare(getHeight(), 0.0) <= 0) {
            if (getPrefWidth() > 0 && getPrefHeight() > 0) {
                setPrefSize(getPrefWidth(), getPrefHeight());
            } else {
                setPrefSize(PREFERRED_WIDTH, PREFERRED_HEIGHT);
            }
        }

        getStyleClass().setAll("chart", "xy-chart");

        canvas = new Canvas(PREFERRED_WIDTH, PREFERRED_HEIGHT);
        ctx    = canvas.getGraphicsContext2D();

        pane = new Pane(canvas);

        getChildren().setAll(pane);
    }

    private void registerListeners() {
        widthProperty().addListener(o -> resize());
        heightProperty().addListener(o -> resize());

        listOfSeries.forEach(series -> series.setOnSeriesEvent(seriesEvent -> redraw()));
    }


    // ******************** Methods *******************************************
    @Override protected double computeMinWidth(final double HEIGHT)  { return MINIMUM_WIDTH; }
    @Override protected double computeMinHeight(final double WIDTH)  { return MINIMUM_HEIGHT; }
    @Override protected double computePrefWidth(final double HEIGHT) { return super.computePrefWidth(HEIGHT); }
    @Override protected double computePrefHeight(final double WIDTH) { return super.computePrefHeight(WIDTH); }
    @Override protected double computeMaxWidth(final double HEIGHT)  { return MAXIMUM_WIDTH; }
    @Override protected double computeMaxHeight(final double WIDTH)  { return MAXIMUM_HEIGHT; }

    @Override public ObservableList<Node> getChildren() { return super.getChildren(); }

    public Paint getChartBackgroundPaint() { return null == chartBackgroundPaint ? _chartBackgroundPaint : chartBackgroundPaint.get(); }
    public void setChartBackgroundPaint(final Paint PAINT) {
        if (null == chartBackgroundPaint) {
            _chartBackgroundPaint = PAINT;
            redraw();
        } else {
            chartBackgroundPaint.set(PAINT);
        }
    }
    public ObjectProperty<Paint> chartBackgroundPaintProperty() {
        if (null == chartBackgroundPaint) {
            chartBackgroundPaint = new ObjectPropertyBase<Paint>(_chartBackgroundPaint) {
                @Override protected void invalidated() { redraw(); }
                @Override public Object getBean() { return YPane.this; }
                @Override public String getName() { return "chartBackgroundPaint"; }
            };
            _chartBackgroundPaint = null;
        }
        return chartBackgroundPaint;
    }

    public double getRangeY() { return null == rangeY ? _rangeY : rangeY.get(); }
    public void setRangeY(final double VALUE) {
        if (null == rangeY) {
            _rangeY = VALUE;
            resize();
        } else {
            rangeY.set(VALUE);
        }
    }
    public DoubleProperty rangeYProperty() {
        if (null == rangeY) {
            rangeY = new DoublePropertyBase(_rangeY) {
                @Override protected void invalidated() { resize(); }
                @Override public Object getBean() { return YPane.this; }
                @Override public String getName() { return "rangeY"; }
            };
        }
        return rangeY;
    }

    public List<YSeries<T>> getListOfSeries() { return listOfSeries; }


    // ******************** Draw Chart ****************************************
    private void drawChart() {
        if (null == listOfSeries || listOfSeries.isEmpty()) return;

        ctx.clearRect(0, 0, width, height);
        ctx.setFill(getChartBackgroundPaint());
        ctx.fillRect(0, 0, width, height);

        double    minValue = listOfSeries.stream().mapToDouble(YSeries::getMin).min().getAsDouble();
        ChartType type     = listOfSeries.get(0).getChartType();

        listOfSeries.forEach(series -> {
            final ChartType TYPE = series.getChartType();
            switch(TYPE) {
                case DONUT               : drawDonut(series); break;
                case RADAR_POLYGON       :
                case SMOOTH_RADAR_POLYGON:
                case RADAR_SECTOR        : drawRadar(series, type, minValue); break;
            }
        });
    }

    private void drawDonut(final YSeries<T> SERIES) {
        if (null == SERIES) return;
        List<YData> items       = SERIES.getItems();
        int         noOfItems   = items.size();
        double      center      = size * 0.5;
        double      innerRadius = size * 0.275;
        double      outerRadius = size * 0.4;
        double      barWidth    = size * 0.1;
        double      sum         = items.stream().mapToDouble(YData::getY).sum();
        double      stepSize    = 360.0 / sum;
        double      angle       = 0;
        double      startAngle  = 90;
        double      xy          = size * 0.1;
        double      wh          = size * 0.8;
        double      x;
        double      y;
        
        ctx.setLineCap(StrokeLineCap.BUTT);
        ctx.setTextAlign(TextAlignment.CENTER);
        ctx.setTextBaseline(VPos.CENTER);

        for (YData item : items) {
            double value = item.getY();
            startAngle -= angle;
            angle = value * stepSize;

            // Segment
            ctx.setLineWidth(barWidth);
            ctx.setStroke(item.getColor());
            ctx.strokeArc(xy, xy, wh, wh, startAngle, -angle, ArcType.OPEN);

            // Percentage
            //x = innerRadius * Math.cos(Math.toRadians(startAngle - (angle * 0.5)));
            //y = -innerRadius * Math.sin(Math.toRadians(startAngle - (angle * 0.5)));
            //ctx.setFill(Color.BLACK);
            //ctx.fillText(String.format(Locale.US, "%.0f%%", (value / sum * 100.0)), center + x, center + y, barWidth);

            // Value
            x = outerRadius * Math.cos(Math.toRadians(startAngle - (angle * 0.5)));
            y = -outerRadius * Math.sin(Math.toRadians(startAngle - (angle * 0.5)));
            ctx.setFill(Color.WHITE);
            ctx.fillText(String.format(Locale.US, "%.0f", value), center + x, center + y, barWidth);
        }
    }

    private void drawRadar(final YSeries<T> SERIES, final ChartType TYPE, final double MIN_VALUE) {
        final double CENTER_X      = 0.5 * size;
        final double CENTER_Y      = CENTER_X;
        final double CIRCLE_SIZE   = 0.9 * size;
        final double DATA_RANGE    = getRangeY();
        final double RANGE         = 0.35714 * CIRCLE_SIZE;
        final double OFFSET        = 0.14286 * CIRCLE_SIZE;
        final int    NO_OF_SECTORS = SERIES.getItems().size();
        final double angleStep     = 360.0 / NO_OF_SECTORS;

        // draw the chart data
        ctx.save();
        if (SERIES.getFill() instanceof RadialGradient) {
            ctx.setFill(new RadialGradient(0, 0, size  * 0.5, size * 0.5, size * 0.45, false, CycleMethod.NO_CYCLE, ((RadialGradient) SERIES.getFill()).getStops()));
        } else {
            ctx.setFill(SERIES.getFill());
        }
        ctx.setStroke(SERIES.getStroke());

        switch(SERIES.getChartType()) {
            case RADAR_POLYGON:
                ctx.save();
                ctx.beginPath();
                ctx.moveTo(CENTER_X, 0.36239 * size);
                SERIES.getItems().forEach(item -> {
                    double r1 = (item.getY() - MIN_VALUE) / DATA_RANGE;
                    ctx.lineTo(CENTER_X, CENTER_Y - OFFSET - r1 * RANGE);
                    ctx.translate(CENTER_X, CENTER_Y);
                    ctx.rotate(angleStep);
                    ctx.translate(-CENTER_X, -CENTER_Y);
                });
                double r2 = ((SERIES.getItems().get(NO_OF_SECTORS - 1).getY() - MIN_VALUE) / DATA_RANGE);
                ctx.lineTo(CENTER_X, CENTER_Y - OFFSET - r2 * RANGE);
                ctx.closePath();
                ctx.fill();
                ctx.stroke();
                ctx.restore();
                break;
            case SMOOTH_RADAR_POLYGON:
                double      radAngle     = Math.toRadians(180);
                double      radAngleStep = Math.toRadians(angleStep);
                List<Point> points       = new ArrayList<>();

                double x = CENTER_X + (-Math.sin(radAngle) * (CENTER_Y - (0.36239 * size)));
                double y = CENTER_Y + (+Math.cos(radAngle) * (CENTER_Y - (0.36239 * size)));
                points.add(new Point(x, y));

                for (YData item : SERIES.getItems()) {
                    double r1 = ((item.getY() - MIN_VALUE) / DATA_RANGE);
                    x = CENTER_X + (-Math.sin(radAngle) * (CENTER_Y - (CENTER_Y - OFFSET - r1 * RANGE)));
                    y = CENTER_Y + (+Math.cos(radAngle) * (CENTER_Y - (CENTER_Y - OFFSET - r1 * RANGE)));
                    points.add(new Point(x, y));
                    radAngle += radAngleStep;
                }

                x = CENTER_X + (-Math.sin(radAngle) * (CENTER_Y - (CENTER_Y - OFFSET - ((SERIES.getItems().get(NO_OF_SECTORS - 1).getY() - MIN_VALUE) / DATA_RANGE) * RANGE)));
                y = CENTER_Y + (+Math.cos(radAngle) * (CENTER_Y - (CENTER_Y - OFFSET - ((SERIES.getItems().get(NO_OF_SECTORS - 1).getY() - MIN_VALUE) / DATA_RANGE) * RANGE)));
                points.add(new Point(x, y));

                Point[] interpolatedPoints = Helper.subdividePoints(points.toArray(new Point[0]), 8);

                ctx.beginPath();
                ctx.moveTo(interpolatedPoints[0].getX(), interpolatedPoints[0].getY());
                for (int i = 0 ; i < interpolatedPoints.length - 1 ; i++) {
                    Point point = interpolatedPoints[i];
                    ctx.lineTo(point.getX(), point.getY());
                }
                ctx.lineTo(interpolatedPoints[interpolatedPoints.length - 1].getX(), interpolatedPoints[interpolatedPoints.length - 1].getY());
                ctx.closePath();

                ctx.fill();
                ctx.stroke();
                break;
            case RADAR_SECTOR:
                ctx.translate(CENTER_X, CENTER_Y);
                ctx.rotate(-90);
                ctx.translate(-CENTER_X, -CENTER_Y);
                SERIES.getItems().forEach(item -> {
                    double r1 = (item.getY() - MIN_VALUE) / DATA_RANGE;
                    ctx.beginPath();
                    ctx.moveTo(CENTER_X, CENTER_Y);
                    ctx.arc(CENTER_X, CENTER_Y, r1 * RANGE + OFFSET, r1 * RANGE + OFFSET, 0, -angleStep);
                    ctx.closePath();
                    ctx.fill();
                    ctx.stroke();

                    ctx.translate(CENTER_X, CENTER_Y);
                    ctx.rotate(angleStep);
                    ctx.translate(-CENTER_X, -CENTER_Y);
                });
                break;
        }
        ctx.restore();

        drawRadarOverlay(NO_OF_SECTORS, TYPE);
    }

    private void drawRadarOverlay(final int NO_OF_SECTORS, final ChartType TYPE) {
        final double CENTER_X    = 0.5 * size;
        final double CENTER_Y    = CENTER_X;
        final double CIRCLE_SIZE = 0.90 * size;
        //final double DATA_RANGE  = getRangeY();
        //final double RANGE       = 0.35714 * CIRCLE_SIZE;
        //final double OFFSET      = 0.14286 * CIRCLE_SIZE;
        final double angleStep   = 360.0 / NO_OF_SECTORS;

        // draw concentric rings
        ctx.setLineWidth(1);
        ctx.setStroke(Color.GRAY);
        double ringStepSize = size / 20.0;
        double pos          = 0.5 * (size - CIRCLE_SIZE);
        double ringSize     = CIRCLE_SIZE;
        for (int i = 0 ; i < 11 ; i++) {
            ctx.strokeOval(pos, pos, ringSize, ringSize);
            pos      += ringStepSize;
            ringSize -= 2 * ringStepSize;
        }

        // draw star lines
        ctx.save();
        for (int i = 0 ; i < NO_OF_SECTORS ; i++) {
            ctx.strokeLine(CENTER_X, 0.05 * size, CENTER_X, 0.5 * size);
            ctx.translate(CENTER_X, CENTER_Y);
            ctx.rotate(angleStep);
            ctx.translate(-CENTER_X, -CENTER_Y);
        }
        ctx.restore();

        // draw threshold line
        /*
        if (isThresholdVisible()) {
            radius = ((threshold.get() - MIN_VALUE) / DATA_RANGE);
            ctx.setLineWidth(clamp(1d, 3d, size * 0.005));
            ctx.setStroke(getThresholdColor());
            ctx.strokeOval(0.5 * size - OFFSET - radius * RANGE, 0.5 * size - OFFSET - radius * RANGE,
                                  2 * (radius * RANGE + OFFSET), 2 * (radius * RANGE + OFFSET));
        }
        */

        // prerotate if sectormode
        ctx.save();

        if (ChartType.RADAR_SECTOR == TYPE) {
            ctx.translate(CENTER_X, CENTER_Y);
            ctx.rotate(angleStep * 0.5);
            ctx.translate(-CENTER_X, -CENTER_Y);
        }

        // draw text
        ctx.save();
        ctx.setFont(Fonts.latoRegular(0.04 * size));
        ctx.setTextAlign(TextAlignment.CENTER);
        ctx.setTextBaseline(VPos.CENTER);
        ctx.setFill(Color.BLACK);
        for (int i = 0 ; i < NO_OF_SECTORS ; i++) {
            //ctx.fillText(data.get(i).ID, CENTER_X, size * 0.02);
            ctx.translate(CENTER_X, CENTER_Y);
            ctx.rotate(angleStep);
            ctx.translate(-CENTER_X, -CENTER_Y);
        }
        ctx.restore();

        ctx.restore();
    }


    // ******************** Resizing ******************************************
    private void resize() {
        width  = getWidth() - getInsets().getLeft() - getInsets().getRight();
        height = getHeight() - getInsets().getTop() - getInsets().getBottom();
        size   = width < height ? width : height;

        if (keepAspect) {
            if (aspectRatio * width > height) {
                width = 1 / (aspectRatio / height);
            } else if (1 / (aspectRatio / height) > width) {
                height = aspectRatio * width;
            }
        }

        if (width > 0 && height > 0) {
            pane.setMaxSize(width, height);
            pane.setPrefSize(width, height);
            pane.relocate((getWidth() - width) * 0.5, (getHeight() - height) * 0.5);

            canvas.setWidth(size);
            canvas.setHeight(size);
            canvas.relocate((width - size) * 0.5, (height - size) * 0.5);
            
            redraw();
        }
    }

    protected void redraw() {
        drawChart();
    }
}