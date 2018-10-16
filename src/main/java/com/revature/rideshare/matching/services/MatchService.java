package com.revature.rideshare.matching.services;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import com.revature.rideshare.matching.algorithm.AggregateRankingBuilder;
import com.revature.rideshare.matching.algorithm.RankByAffect;
import com.revature.rideshare.matching.algorithm.RankByBatchEnd;
import com.revature.rideshare.matching.algorithm.RankByDistance;
import com.revature.rideshare.matching.beans.User;
import com.revature.rideshare.matching.clients.UserClient;

/**
 * The main service class for finding drivers who match a given rider.
 */
@Service
public class MatchService {
	/**
	 * The maximum number of matches to find.
	 */
	private static final int MAX_MATCHES = 10;
	
	/**
	 * The coefficients used to weight importance of matching features.
	 */
	
	private static final double DISTANCE_COEFFICIENT = 1;
	private static final double BATCH_END_COEFFICIENT = 4;
	private static final double AFFECT_COEFFICENT = 1;
	// private static final double START_TIME_COEFFICIENT = 0;

	/**
	 * The role corresponding to a potential driver.
	 */
	private static final String DRIVER_ROLE = "DRIVER";

	@Autowired
	private UserClient userClient;
	
	private static RankByAffect rankByAffect;
	private static RankByBatchEnd rankByBatchEnd;
	private static RankByDistance rankByDistance;
	
	{
		rankByAffect = new RankByAffect();
		rankByBatchEnd = new RankByBatchEnd();
		rankByDistance = new RankByDistance();
		
		rankByAffect.setWeight(AFFECT_COEFFICENT);
		rankByBatchEnd.setWeight(BATCH_END_COEFFICIENT);
		rankByDistance.setWeight(DISTANCE_COEFFICIENT);
		
	}

	/**
	 * An association of a user with a rank.
	 */
	private static class RankedUser implements Comparable<RankedUser> {
		/**
		 * The user in the association.
		 */
		public User user;
		/**
		 * The matching rank, as defined by
		 * {@link com.revature.rideshare.matching.services.MatchService#rankMatch(User, User)
		 * rankMatch}.
		 */
		public double rank;

		public RankedUser(User user, double rank) {
			this.user = user;
			this.rank = rank;
		}

		@Override
		public int compareTo(RankedUser o) {
			return Double.compare(rank, o.rank);
		}
	}
	
	/**
	 * Finds matched drivers for given riders by distance. Association formed
	 * between driver and a ranking, with ranking discarded after sorting.
	 * 
	 * @param rider the user looking for a ride
	 * @return unranked list of matched drivers, sorted by distance in descending
	 *         order (up to {@link #MAX_MATCHES})
	 */
	public List<User> findMatchesByDistance(User rider) {
		int officeId = officeLinkToId(rider.getOffice());
		AggregateRankingBuilder arb = new AggregateRankingBuilder();
		arb.addCriterion(rankByDistance);

		return userClient.findByOfficeAndRole(officeId, DRIVER_ROLE).stream()
				.map(driver -> new RankedUser(driver, arb.rankMatch(rider, driver)))
				.sorted(Comparator.reverseOrder())
				.limit(MAX_MATCHES)
				.map(rankedUser -> rankedUser.user)
				.collect(Collectors.toList());
	}

	/**
	 * Finds matched drivers for given rider by office location only, based on if
	 * they are liked or disliked drivers (affected drivers).
	 * 
	 * @param rider the user looking for a ride
	 * @return unranked list of matched drivers, minus affected (up to
	 *         {@link #MAX_MATCHES})
	 */
	public List<User> findMatchesByAffects(User rider) {
		int officeId = officeLinkToId(rider.getOffice());
		AggregateRankingBuilder arb = new AggregateRankingBuilder();
		arb.addCriterion(rankByAffect);

		return userClient.findByOfficeAndRole(officeId, DRIVER_ROLE).stream()
				.map(driver -> new RankedUser(driver, arb.rankMatch(rider, driver)))
				.sorted(Comparator.reverseOrder())
				.limit(MAX_MATCHES)
				.map(rankedUser -> rankedUser.user)
				.collect(Collectors.toList());
	}

	/**
	 * Finds matched drivers for rider based on the batch's end date. Association
	 * formed between driver and a ranking, with rank discarded after sorting.
	 * 
	 * @param rider the user looking for a ride
	 * @return unranked list of matched drivers, sorted by batch end date in
	 *         descending order
	 */
	public List<User> findMatchesByBatchEnd(User rider) {
		int officeId = officeLinkToId(rider.getOffice());
		AggregateRankingBuilder arb = new AggregateRankingBuilder();
		arb.addCriterion(rankByBatchEnd);

		return userClient.findByOfficeAndRole(officeId, DRIVER_ROLE).stream()
				.map(driver -> new RankedUser(driver, arb.rankMatch(rider, driver)))
				.sorted(Comparator.reverseOrder())
				.limit(MAX_MATCHES)
				.map(rankedUser -> rankedUser.user)
				.collect(Collectors.toList());
	}

	/**
	 * Finds matched drivers for rider based on a weighted rank from distance batch
	 * end, and whether they are liked or disliked drivers (affected drivers).
	 * 
	 * @param rider the user for whom to find a driver
	 * @return list of matched drivers, sorted by nearest distance and closest batch
	 *         end date in descending order (up to {@link #MAX_MATCHES})
	 */
	public List<User> findMatches(User rider) {
		int officeId = officeLinkToId(rider.getOffice());
		AggregateRankingBuilder arb = new AggregateRankingBuilder();
		arb.addCriterion(rankByAffect);
		arb.addCriterion(rankByBatchEnd);
		arb.addCriterion(rankByDistance);

		return userClient.findByOfficeAndRole(officeId, DRIVER_ROLE).stream()
				.map(driver -> new RankedUser(driver, arb.rankMatch(rider, driver)))
				.sorted(Comparator.reverseOrder()).limit(MAX_MATCHES).map(rankedUser -> rankedUser.user)
				.collect(Collectors.toList());
	}

	// TODO: Add in batch start time as a weighted value; add in method for
	// getting batch start time

	/**
	 * Extracts the office ID from an office link.
	 * 
	 * @param link a link to an office (e.g. "/offices/2")
	 * @return the ID of the office
	 * @throws IllegalArgumentException if the given link is invalid
	 */
	private int officeLinkToId(String link) {
		AntPathMatcher matcher = new AntPathMatcher();
		try {
			return Integer.parseInt(matcher.extractUriTemplateVariables("/offices/{id}", link).get("id"));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(link + " is not a valid office link.");
		}
	}
}