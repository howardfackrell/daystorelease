package com.octanner.plugins;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * @goal daystorelease
 * @aggregator
 *
 * @phase process-sources
 */
public class MyMojo
    extends AbstractMojo
{
    /**
     * @parameter property = "lastBranch"
     */
    private String _lastBranch;

    /**
     * @parameter property = "thisBranch"
     */
    private String _thisBranch;

    /**
     * @parameter property = "releaseDate"
     */
    private String _releaseDate;

    public void execute()
        throws MojoExecutionException
    {
        validateParameters();
        List<Commit> commitList = getCommits(_lastBranch, _thisBranch);
        Date productionDate = getDateFromString(_releaseDate);

        float averageDaysForCommit = averageDifferenceInDays(commitList, productionDate);
        System.out.println(commitList.size() +
                " commits.  Average " + averageDaysForCommit + " days from commit to production");
    }

    private void validateParameters() throws MojoExecutionException {
        StringBuilder error = new StringBuilder();
        if (_lastBranch == null) {
            error.append("No lastBranch value specified.  ");
        }
        if (_thisBranch == null) {
            error.append("No thisBranch value specified.  ");
        }
        try {
            getDateFromString(_releaseDate);
        } catch (MojoExecutionException e) {
            error.append("Can't parse the release date, it must be formatted yyyy-MM-dd");
        }

        if (error.length() > 0) {
            error.append("Usage:  mvn com.octanner.plugins:daystorelease:daystorelease -DlastBranch=release_14_2_1 -DthisBranch=HEAD -DreleaseDate=2014-03-20");
            throw new MojoExecutionException(error.toString());
        }
    }

    private List<Commit> getCommits(String fromBranch, String toBranch) throws MojoExecutionException {
        //git log --no-merges --date=short --pretty=format:"%ad %an %s" release_14_2_1..HEAD
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.redirectErrorStream(true);

        processBuilder.command(
                "git", "log", "--no-merges", "--date=short", "--pretty=format:\"%ad|%an|%s\"", fromBranch+ ".."+ toBranch);

        System.out.println(join(processBuilder.command(), " "));
        List<Commit> commitList = new ArrayList<Commit>();
        try {
            Process process = processBuilder.start();

            BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while((line = input.readLine()) != null) {
                commitList.add(new Commit(line));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return commitList;
    }

    private float averageDifferenceInDays(List<Commit> commitList, Date productionDate) {
        long totalDays = 0;
        for (Commit commit : commitList) {
            long daysForCommit = commit.daysUntil(productionDate);
            totalDays += daysForCommit;
        }
        float averageDays = 0;
        if (commitList.size() > 0) {
            averageDays = totalDays / commitList.size();
        }
        return averageDays;
    }

    private String join(List<String> stringList, String separator) {
        StringBuilder sb = new StringBuilder();
        for (String s : stringList) {
            sb.append(s).append(separator);
        }
        return sb.toString();
    }

    Date getDateFromString(String dateString) throws MojoExecutionException {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Date date = null;
        try {
            date = format.parse(dateString.replace("\"", ""));
        } catch (ParseException e) {
            throw new MojoExecutionException("Please enter required dates in the format yyyy-MM-dd", e);
        }
        return date;
    }

    class Commit {
        Date date;
        String message;
        Commit(String logLine) throws MojoExecutionException {
            message = logLine;
            String dateString = logLine.substring(0, logLine.lastIndexOf(" ")).replace("\"", " ");
            date = getDateFromString(dateString);
        }

        long daysUntil(Date releaseDate) {
            long millis = releaseDate.getTime() - date.getTime();
            long days = millis / (1000 * 60 * 60 * 24);
            return days;
        }
    }

}
