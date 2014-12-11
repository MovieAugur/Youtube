import com.google.gdata.client.youtube.*;
import com.google.gdata.data.Link;
import com.google.gdata.data.geo.impl.*;
import com.google.gdata.data.media.mediarss.*;
import com.google.gdata.data.youtube.*;
import com.google.gdata.data.extensions.*;
import com.google.gdata.util.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class YoutubeReader {

	/**
	 * The name of the server hosting the YouTube GDATA feeds
	 */
	public static final String YOUTUBE_GDATA_SERVER = "http://gdata.youtube.com";

	/**
	 * The prefix common to all standard feeds
	 */
	public static final String VIDEOS_API_PREFIX = YOUTUBE_GDATA_SERVER
			+ "/feeds/api/videos";

	public static final String TEXT_DATA = "T";

	public static final String NUMERIC_DATA = "N";

	public static final String LIKES = "L";

	public static final String DISLIKES = "D";

	public static final String VIEWCOUNT = "V";

	public static final String COMMENTS = "C";

	public static final String YOUTUBE = "Y";

	public static final String FILE_SUFFIX = "yt_";

	private static YouTubeService service = new YouTubeService("Augur");

	private static S3Utility utility;

	private static List<MovieData> movieList = new ArrayList<MovieData>();

	public static void main(String[] args) {
		// List<String> movieList = new ArrayList<String>();
		String bucketName = args[0];
		String path = args[1];
		utility = new S3Utility(bucketName, path);
		/*File inputFile = new File("movies2013.txt");
		try {
			BufferedReader br = new BufferedReader(new FileReader(inputFile));
			String line;
			while ((line = br.readLine()) != null) {
				StringTokenizer tokenizer = new StringTokenizer(line, "\t");
				String movie = tokenizer.nextToken();
				System.out.println(movie);
				utility.uploadFile(fetchDataAndGenerateInput(movie));
			}
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		System.out.println("Starting youtube job...");
		for (int i = 2; i < args.length; i++) {
			String movie = args[i].replace("_", " ");
			utility.uploadFile(fetchDataAndGenerateInput(movie));
		}
		 
	}

	public static String fetchDataAndGenerateInput(String movieName) {
		MovieData movie = new MovieData();
		VideoFeed videoFeed = new VideoFeed();
		movie.setMovieName(movieName);
		try {
			YouTubeQuery query = new YouTubeQuery(new URL(VIDEOS_API_PREFIX));
			query.setOrderBy(YouTubeQuery.OrderBy.RELEVANCE);
			query.setFullTextQuery(movieName + " trailer");
			// query.setAuthor("movieclipsTRAILERS");
			query.setMaxResults(1);
			videoFeed = service.query(query, VideoFeed.class);
			printVideoFeed(videoFeed, true);
		} catch (IOException | ServiceException e) {
			e.printStackTrace();
		}
		populateTrailerStatistics(movie, videoFeed);
		populateCommentsList(movie, videoFeed);
		movieList.add(movie);
		return generateInputFile(movie);
	}

	private static String generateInputFile(MovieData movie) {
		String fileName = FILE_SUFFIX + movie.getMovieName().replace(":","") + ".txt";
		File inputFile = new File(fileName);
		FileWriter fileWriter;
		String commentMetadata = movie.getMovieName() + "\t" + TEXT_DATA
				+ YOUTUBE + COMMENTS;
		try {

			inputFile.createNewFile();
			fileWriter = new FileWriter(inputFile);
			BufferedWriter bw = new BufferedWriter(fileWriter);
			for (String comment : movie.getComments()) {
				String data = commentMetadata + "\t" + comment;
				bw.write(data);
				bw.newLine();
			}
			bw.write(movie.getMovieName() + "\t" + NUMERIC_DATA + YOUTUBE
					+ LIKES + "\t" + movie.getNoOfLikes());
			bw.newLine();
			bw.write(movie.getMovieName() + "\t" + NUMERIC_DATA + YOUTUBE
					+ DISLIKES + "\t" + movie.getNoOfDislikes());
			bw.newLine();
			bw.write(movie.getMovieName() + "\t" + NUMERIC_DATA + YOUTUBE
					+ VIEWCOUNT + "\t" + movie.getViewCount());
			bw.newLine();
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Created file..." + fileName);
		return fileName;
	}

	private static void populateTrailerStatistics(MovieData movie,
			VideoFeed videoFeed) {
		for (VideoEntry videoEntry : videoFeed.getEntries()) {
			YtStatistics stats = videoEntry.getStatistics();
			YtRating rating = videoEntry.getYtRating();
			movie.setViewCount(stats.getViewCount());
			if (rating != null) {
				movie.setNoOfDislikes(rating.getNumDislikes());
				movie.setNoOfLikes(rating.getNumLikes());
			}
		}
	}

	private static void populateCommentsList(MovieData movie,
			VideoFeed videoFeed) {
		List<String> commentsList = new LinkedList<String>();
		for (VideoEntry videoEntry : videoFeed.getEntries()) {
			Comments comments = videoEntry.getComments();
			CommentFeed commentsFeed = new CommentFeed();
			if (comments != null) {
				try {
					String initialLink = comments.getFeedLink().getHref();
					initialLink = initialLink
							+ "&orderby=published&max-results=50";
					commentsFeed = service.getFeed(new URL(initialLink),
							CommentFeed.class);
					System.out.println(initialLink);
					Link nextLink;
					while (true) {
						for (CommentEntry commentEntry : commentsFeed
								.getEntries()) {
							String comment = commentEntry.getTextContent()
									.getContent().getPlainText();
							comment = comment.replace("\n", "");
							commentsList.add(comment);
							/*
							 * System.out.println(commentEntry.getTextContent()
							 * .getContent().getPlainText());
							 */
						}
						nextLink = commentsFeed.getLink("next", null);
						// System.out.println(nextLink.getHref());
						if (nextLink == null) {
							System.out.println("breaking out");
							break;
						}
						try {
							Thread.sleep(3000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							return;
						}
						commentsFeed = service.getFeed(
								new URL(nextLink.getHref()), CommentFeed.class);
					}
				} catch (IOException | ServiceException e) {
					System.out.println("Error in fetching comments: ");
					e.printStackTrace();
				}
			}
		}
		movie.setComments(commentsList);
	}

	public static void printVideoFeed(VideoFeed videoFeed, boolean detailed) {
		for (VideoEntry videoEntry : videoFeed.getEntries()) {
			printVideoEntry(videoEntry, detailed);
			// printCommentsFeed(videoEntry);
		}
	}

	public static void printVideoEntry(VideoEntry videoEntry, boolean detailed) {
		System.out.println("Title: " + videoEntry.getTitle().getPlainText());

		if (videoEntry.isDraft()) {
			System.out.println("Video is not live");
			YtPublicationState pubState = videoEntry.getPublicationState();
			if (pubState.getState() == YtPublicationState.State.PROCESSING) {
				System.out.println("Video is still being processed.");
			} else if (pubState.getState() == YtPublicationState.State.REJECTED) {
				System.out.print("Video has been rejected because: ");
				System.out.println(pubState.getDescription());
				System.out.print("For help visit: ");
				System.out.println(pubState.getHelpUrl());
			} else if (pubState.getState() == YtPublicationState.State.FAILED) {
				System.out.print("Video failed uploading because: ");
				System.out.println(pubState.getDescription());
				System.out.print("For help visit: ");
				System.out.println(pubState.getHelpUrl());
			}
		}

		if (videoEntry.getEditLink() != null) {
			System.out.println("Video is editable by current user.");
		}

		if (detailed) {

			YouTubeMediaGroup mediaGroup = videoEntry.getMediaGroup();

			System.out.println("Uploaded by: " + mediaGroup.getUploader());

			System.out.println("Video ID: " + mediaGroup.getVideoId());
			System.out.println("Description: "
					+ mediaGroup.getDescription().getPlainTextContent());

			MediaPlayer mediaPlayer = mediaGroup.getPlayer();
			System.out.println("Web Player URL: " + mediaPlayer.getUrl());
			MediaKeywords keywords = mediaGroup.getKeywords();
			System.out.print("Keywords: ");
			for (String keyword : keywords.getKeywords()) {
				System.out.print(keyword + ",");
			}

			GeoRssWhere location = videoEntry.getGeoCoordinates();
			if (location != null) {
				System.out.println("Latitude: " + location.getLatitude());
				System.out.println("Longitude: " + location.getLongitude());
			}

			Rating rating = videoEntry.getRating();
			YtRating ytrating = videoEntry.getYtRating();
			if (rating != null) {
				System.out.println("Average rating: " + rating.getAverage());
				System.out.println("Rel: " + rating.getRel());
				System.out.println("Min: " + rating.getMin());
				System.out.println("Max: " + rating.getMax());
				System.out.println("No. :" + rating.getNumRaters());
			}

			if (ytrating != null) {
				System.out.println("Likes: " + ytrating.getNumLikes());
				System.out.println("Dislikes: " + ytrating.getNumDislikes());
			}

			YtStatistics stats = videoEntry.getStatistics();
			if (stats != null) {
				System.out.println("View count: " + stats.getViewCount());
				System.out.println("Favourite count: "
						+ stats.getFavoriteCount());
			}
			System.out.println();

			/*
			 * System.out.println("\tThumbnails:"); for (MediaThumbnail
			 * mediaThumbnail : mediaGroup.getThumbnails()) {
			 * System.out.println("\t\tThumbnail URL: " +
			 * mediaThumbnail.getUrl());
			 * System.out.println("\t\tThumbnail Time Index: " +
			 * mediaThumbnail.getTime()); System.out.println(); }
			 * 
			 * System.out.println("\tMedia:"); for (YouTubeMediaContent
			 * mediaContent : mediaGroup .getYouTubeContents()) {
			 * System.out.println("\t\tMedia Location: " +
			 * mediaContent.getUrl()); System.out.println("\t\tMedia Type: " +
			 * mediaContent.getType()); System.out.println("\t\tDuration: " +
			 * mediaContent.getDuration()); System.out.println(); }
			 */

			for (YouTubeMediaRating mediaRating : mediaGroup
					.getYouTubeRatings()) {
				System.out
						.println("Video restricted in the following countries: "
								+ mediaRating.getCountries().toString());
			}
		}
	}

	@SuppressWarnings("unused")
	private static void printCommentsFeed(VideoEntry videoEntry) {
		Comments comments = videoEntry.getComments();
		try {
			CommentFeed commentsFeed = service.getFeed(new URL(comments
					.getFeedLink().getHref()), CommentFeed.class);
			System.out.println("Comments: ");
			Link nextLink;
			while (true) {
				for (CommentEntry commentEntry : commentsFeed.getEntries()) {
					System.out.println(commentEntry.getTextContent()
							.getContent().getPlainText());
				}
				nextLink = commentsFeed.getLink("next", null);
				if (nextLink == null) {
					break;
				}
				commentsFeed = service.getFeed(new URL(nextLink.getHref()),
						CommentFeed.class);
			}
		} catch (IOException | ServiceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
