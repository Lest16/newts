package org.opennms.newts.api;


import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;


public class Aggregates {

    public static class Point {
        public Timestamp x;
        public ValueType<?> y;

        public Point(Timestamp x, ValueType<?> y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return String.format("%s[%s, %s]", getClass().getSimpleName(), x, y);
        }

    }

    static class Timestamps implements Iterable<Timestamp>, Iterator<Timestamp> {

        public static final Duration DEFAULT_STEP_SIZE = Duration.seconds(300);

        private final Duration m_stepSize;

        private Timestamp m_current;
        private Timestamp m_final;

        public Timestamps(Timestamp start, Timestamp end) {
            this(start, end, DEFAULT_STEP_SIZE);
        }

        public Timestamps(Timestamp start, Timestamp finish, final Duration stepSize) {
            m_current = start.stepCeiling(stepSize);
            m_final = finish.stepCeiling(stepSize);
            m_stepSize = stepSize;
        }

        @Override
        public boolean hasNext() {
            return m_current.lte(m_final);
        }

        @Override
        public Timestamp next() {

            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            try {
                return m_current;
            }
            finally {
                m_current = m_current.plus(m_stepSize);
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<Timestamp> iterator() {
            return this;
        }

    }

    static class RateFunction implements Function<Point, Point> {

        private Point m_previous = null;

        @Override
        public Point apply(Point input) {

            if (m_previous == null) {
                m_previous = input;
                return null;
            }

            ValueType<?> rate = getRate(input);
            m_previous = input;

            return new Point(input.x, rate);
        }

        private ValueType<?> getRate(Point point) {
            return getCount(point).divideBy(getElapsedSeconds(point.x));
        }

        private ValueType<?> getCount(Point point) {
            return point.y.delta(m_previous.y);
        }

        private long getElapsedSeconds(Timestamp ts) {
            return ts.asSeconds() - m_previous.x.asSeconds();
        }

    }

    public static Collection<Point> rate(Collection<Point> points) {
        return Collections2.transform(points, new RateFunction());
    }

    public static final long HEARTBEAT = 600000;
    public static final double XFF = 0.5d;

    public static Collection<Point> average(Timestamp start, Timestamp end, Duration stepSize, Collection<Point> points) {

        checkNotNull(start, "start argument");
        checkNotNull(end, "end argument");
        checkNotNull(stepSize, "stepSize argument");
        checkNotNull(points, "points argument");

        List<Point> results = Lists.newArrayList();
        Iterator<Timestamp> steps = new Timestamps(start, end, stepSize);

        Timestamp nextStep = steps.next();
        Timestamp lastUpdate = start;
        ValueType<?> accumulated = new Gauge(0.0d);
        long unknown = lastUpdate.asMillis() % stepSize.asMillis(), known = 0;

        for (Point point : points) {
            // it would probably be better to use 'before the start data if you have it to provide a value for the
            // initial unknown region
            if (point == null || point.x.lt(lastUpdate)) {
                continue;
            }

            long interval;

            if (point.x.gte(nextStep)) {
                interval = nextStep.asMillis() - lastUpdate.asMillis();
            }
            else {
                interval = point.x.asMillis() - lastUpdate.asMillis();
            }

            if (point.y != null && interval < HEARTBEAT) {
                known += interval;
                accumulated = accumulated.plus(point.y.times(interval));
            }
            else {
                unknown += interval;
            }

            if (point.x.gte(nextStep)) {
                ValueType<?> value = null;
                long elapsed = known + unknown;
                if (elapsed > 0 && (unknown / elapsed) < XFF) {
                    value = accumulated.divideBy(known);
                }
                results.add(new Point(nextStep, value));

                // Accumulate the remainder
                interval = point.x.asMillis() - nextStep.asMillis();
                known = interval;
                accumulated = new Gauge(0.0d).plus(point.y.times(interval));

                if (!steps.hasNext()) {
                    break;
                }

                nextStep = steps.next();
                unknown = 0;
            }

            lastUpdate = point.x;

        }

        return results;
    }

    static class Bucketizer {
        private final Timestamp m_start;
        @SuppressWarnings("unused") private final Timestamp m_end;
        private final Duration m_step;
        private final Duration m_heartbeat;
        private final Collection<Point> m_points;
        
        private Timestamp m_lastUpdateTime;
        private double m_accum;
        private double m_lastValue;
        private long m_nanMillis;
        
        Bucketizer(Timestamp start, Timestamp end, Duration step, Duration heartbeat, Collection<Point> points) {
            m_start = start;
            m_end = end;
            m_step = step;
            m_heartbeat = heartbeat;
            m_points = points;
            
            m_lastUpdateTime = m_start;
            m_nanMillis = m_start.minus(m_start.stepFloor(m_step)).asMillis();
            m_lastValue = Double.NaN;
            m_accum = 0.0;
        }
        
        Collection<Point> rollup() {
            List<Point> results = Lists.newArrayList();
            
            for(Point p : m_points) {
                final Timestamp oldTime = m_lastUpdateTime;
                final Timestamp startTime = oldTime.stepFloor(m_step);
                final Timestamp endTime = startTime.plus(m_step);
                final double newValue = p.y.doubleValue();
                final Timestamp newTime = p.x;
                final double oldValue = m_lastValue;
                final double updateValue = calculateUpdate(oldValue, newValue, oldTime, newTime);
                // end 
                m_lastValue = updateValue;
                if (newTime.lt(endTime)) {
                    // the newTime is within the current interval
                    accumulate(oldTime, newTime, updateValue);
                } else {
                    // the newTime is after the end of the current interval
                    final Timestamp boundaryTime = newTime.stepFloor(m_step);

                    // update the accumulators with a value up the boundary
                    accumulate(oldTime, boundaryTime, updateValue);

                    // compute the next value from the accumulator
                    double totalValue = Double.NaN;
                    final long validMillis = boundaryTime.minus(startTime).asMillis() - m_nanMillis;
                    if (m_nanMillis < m_heartbeat.asMillis() && validMillis > 0) {
                        totalValue = m_accum / validMillis;
                    }

                    final long numSteps = boundaryTime.minus(endTime).asMillis() / m_step.asMillis() + 1L;

                    Timestamp nextTime = endTime;
                    for(int i = 0; i < numSteps; i++) {
                        Point n = new Point(nextTime, new Gauge(totalValue));
                        nextTime = nextTime.plus(m_step);
                        results.add(n);
                    }
                    m_nanMillis = 0;
                    m_accum = 0.0;
                    
                    accumulate(boundaryTime, newTime, updateValue);;
                    
                }
                
                m_lastUpdateTime = newTime;
                
                
            }
            
            
            
            
            
            
            
            return results;
        }

        private void accumulate(final Timestamp oldTime, final Timestamp newTime, final double updateValue) {
            Duration elapsed = newTime.minus(oldTime);
            if (Double.isNaN(updateValue)) {
                m_nanMillis = m_nanMillis + elapsed.asMillis();
            }
            else {
                m_accum = m_accum + updateValue * elapsed.asMillis();
            }
        }

        // code here to account for different types - for gauge the update value is the same as the newValue
        private double calculateUpdate(final double oldValue, double newValue, Timestamp oldTime, Timestamp newTime) {
            return newValue;
        }
    }

    public static Collection<Point> rollup2(Timestamp start, Timestamp end, Duration step, Duration heartbeat, Collection<Point> points) {
        return new Bucketizer(start, end, step, heartbeat, points).rollup();
    }

}
