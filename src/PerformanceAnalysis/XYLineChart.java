package PerformanceAnalysis;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.util.ShapeUtils;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class XYLineChart extends JFrame {
    public XYLineChart(String title, List<Integer> xList, List<Double> yList, String xLabel, String yLabel) {
        super(title);
        XYDataset dataset = create_dataset("", xList, yList);
        JFreeChart chart = ChartFactory.createXYLineChart(
                "",
                xLabel,
                yLabel,
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false);
        Shape cross = ShapeUtils.createDiagonalCross(3, 1);
        XYPlot plot = (XYPlot) chart.getPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesLinesVisible(0, true);
        renderer.setSeriesShapesVisible(0, true);
        renderer.setSeriesShape(0, cross);
        plot.setRenderer(renderer);
        ChartPanel panel = new ChartPanel(chart);
        setContentPane(panel);
    }

    private XYDataset create_dataset(String title, List<Integer> xList, List<Double> yList) {
        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries(title);
        for(int i = 0; i < xList.size(); ++i) {
            series.add(xList.get(i), yList.get(i));
        }
        dataset.addSeries(series);
        return dataset;
    }

}
