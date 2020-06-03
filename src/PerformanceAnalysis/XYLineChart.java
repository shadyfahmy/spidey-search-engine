package PerformanceAnalysis;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.util.List;

public class XYLineChart extends JFrame {
    private static final long serialVersionUID = 6294689542092367723L;

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
