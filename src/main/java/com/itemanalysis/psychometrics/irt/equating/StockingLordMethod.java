/*
 * Copyright 2012 J. Patrick Meyer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.itemanalysis.psychometrics.irt.equating;

import com.itemanalysis.psychometrics.analysis.AbstractMultivariateFunction;
import com.itemanalysis.psychometrics.distribution.DistributionApproximation;
import com.itemanalysis.psychometrics.irt.model.ItemResponseModel;
import com.itemanalysis.psychometrics.scaling.LinearTransformation;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunctionGradient;
import org.apache.commons.math3.util.Precision;

import java.util.LinkedHashMap;
import java.util.Set;

public class StockingLordMethod extends AbstractMultivariateFunction implements LinearTransformation {

    private LinkedHashMap<String, ItemResponseModel> itemFormX = null;
    private LinkedHashMap<String, ItemResponseModel> itemFormY = null;
    private DistributionApproximation xDistribution = null;
    private DistributionApproximation yDistribution = null;
    private int xDistributionSize = 0;
    private int yDistributionSize = 0;
    private EquatingCriterionType criterion = null;
    private double intercept = 0.0;
    private double slope = 1.0;
    private int precision = 2;
    Set<String> sY = null;

    public StockingLordMethod(LinkedHashMap<String, ItemResponseModel> itemFormX, LinkedHashMap<String, ItemResponseModel> itemFormY,
                              DistributionApproximation xDistribution, DistributionApproximation yDistribution,
                              EquatingCriterionType criterion)throws DimensionMismatchException{
        this.itemFormX = itemFormX;
        this.itemFormY = itemFormY;
        this.xDistribution = xDistribution;
        this.yDistribution = yDistribution;
        this.criterion = criterion;
        xDistributionSize = xDistribution.getNumberOfPoints();
        yDistributionSize = yDistribution.getNumberOfPoints();
        checkDimensions();
    }

    public StockingLordMethod(LinkedHashMap<String, ItemResponseModel> itemFormX, LinkedHashMap<String, ItemResponseModel> itemFormY,
                              DistributionApproximation yDistribution, EquatingCriterionType criterion)throws DimensionMismatchException{
        this.itemFormX = itemFormX;
        this.itemFormY = itemFormY;
        this.yDistribution = yDistribution;
        this.criterion = EquatingCriterionType.Q1;
        xDistributionSize = xDistribution.getNumberOfPoints();
        yDistributionSize = yDistribution.getNumberOfPoints();
        checkDimensions();
    }

    /**
     * For a common item linking design, both test form must have a set of items that are the same.
     * The parameter estimates will differ, but the common item must be paired. This method checks
     * that the common items are found in both item sets (Form X and Form Y). If not an exception occurs.
     *
     * This method checks that HashMaps have the same number of elements and that the keys in each map
     * are the same. The KeySet from itemFormY (sY) will be used for the keys hereafter.
     *
     *
     * @throws DimensionMismatchException
     */
    private void checkDimensions()throws DimensionMismatchException{
        Set<String> sX = itemFormX.keySet();
        sY = itemFormY.keySet();
        if(sX.size()!=sY.size()) throw new DimensionMismatchException(itemFormX.size(), itemFormY.size());
        int mismatch = 0;
        for(String s : sX){
            if(!sY.contains(s)) mismatch++;
        }
        for(String s : sY){
            if(!sX.contains(s)) mismatch++;
        }
        if(mismatch>0) throw new DimensionMismatchException(mismatch, 0);
    }

    /**
     * Function to be minimized by optimization class
     *
     * Uncmin index starts at 1
     * argument[1]=B (intercept) equating constant
     * argument[2]=A (slope) equating constant
     */
    public double value(double[] coefficient){
        double F = 0.0;
        switch(criterion){
            case Q1: F = getF1(coefficient); break;
            case Q2: F = getF2(coefficient); break;
            case Q1Q2: F = getF1(coefficient) + getF2(coefficient); break;
        }
        return F;
    }

    public ObjectiveFunction getObjectiveFunction(){
        ObjectiveFunction function = new ObjectiveFunction(this);
        return function;
    }

    public ObjectiveFunctionGradient getObjectiveFunctionGradient(){
        ObjectiveFunctionGradient functionGradient = new ObjectiveFunctionGradient(this.gradient());
        return functionGradient;
    }

    /**
     * Criterion function F1 as described in Kim and Kolen (2007).
     *
     * @param coefficient arguments in optimization
     * @return criterion function rho
     */
    public double getF1(double[] coefficient){
        double dif = 0.0;
        double dif2 = 0.0;
        double sum = 0.0;
        double theta = 0.0;
        double weight = 0.0;
        double L1 = 0.0;

        for(int i=0;i<yDistributionSize;i++){
            theta = yDistribution.getPointAt(i);
            weight = yDistribution.getDensityAt(i);
            L1 += weight;
            dif=getFormYTccAtTheta(theta) - getTStarAtTheta(coefficient, theta);
            dif2=Math.pow(dif,2);
            sum+=dif2*weight;
        }
        return sum/L1;
    }

    /**
     * Criterion function F2 as described in Kim and Kolen (2007).
     *
     * @param coefficient arguments in optimization
     * @return criterion function values
     */
    public double getF2(double[] coefficient){
        double dif = 0.0;
        double dif2 = 0.0;
        double sum = 0.0;
        double theta = 0.0;
        double weight = 0.0;
        double L2 = 0;

        for(int i=0;i<xDistributionSize;i++){
            theta = xDistribution.getPointAt(i);
            weight = xDistribution.getDensityAt(i);
            L2 += weight;
            dif=getFormXTccAtTheta(theta)-getTSharpAtTheta(coefficient, theta);
            dif2=Math.pow(dif,2);
            sum+=dif2*weight;
        }
        return sum/L2;
    }

    public double getFormYTccAtTheta(double theta){
        double tcc = 0;
        ItemResponseModel m;
        for(String s: sY){
            m = itemFormY.get(s);
            tcc += m.expectedValue(theta);
        }
        return tcc;
    }

    public double getFormXTccAtTheta(double theta){
        double tcc = 0;
        ItemResponseModel m;
        for(String s : sY){
            m = itemFormX.get(s);
            tcc += m.expectedValue(theta);
        }
        return tcc;
    }

    public double getTStarAtTheta(double[] coefficient, double theta){
        double tStar=0.0;
        ItemResponseModel m;
        for(String s : sY){
            m = itemFormX.get(s);
            tStar += m.tStarExpectedValue(theta, coefficient[0], coefficient[1]);
        }
        return tStar;
    }

    public double getTSharpAtTheta(double[] coefficient, double theta){
        double tSharp = 0.0;
        ItemResponseModel m;
        for(String s : sY){
            m = itemFormY.get(s);
            tSharp += m.tSharpExpectedValue(theta, coefficient[0], coefficient[1]);
        }
        return tSharp;
    }

    public void setIntercept(double intercept){
        this.intercept=intercept;
    }

    public void setScale(double scale){
        this.slope=scale;
    }

    public double getIntercept(){
        return Precision.round(intercept, precision);
    }

    public double getScale(){
        return Precision.round(slope, precision);
    }

    public void setPrecision(int precision){
        this.precision = precision;
    }

    public double transform(double x){
        return slope*x+intercept;
    }

}
