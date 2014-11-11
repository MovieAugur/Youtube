import java.util.List;


public class MovieData {

	private String movieName;
	
	private List<String> comments;
	
	private Integer noOfLikes;
	
	private Integer noOfDislikes;
	
	private Long viewCount;

	public String getMovieName() {
		return movieName;
	}

	public void setMovieName(String movieName) {
		this.movieName = movieName;
	}

	public List<String> getComments() {
		return comments;
	}

	public void setComments(List<String> comments) {
		this.comments = comments;
	}

	public Integer getNoOfLikes() {
		return noOfLikes;
	}

	public void setNoOfLikes(Integer noOfLikes) {
		this.noOfLikes = noOfLikes;
	}

	public Integer getNoOfDislikes() {
		return noOfDislikes;
	}

	public void setNoOfDislikes(Integer noOfDislikes) {
		this.noOfDislikes = noOfDislikes;
	}

	public Long getViewCount() {
		return viewCount;
	}

	public void setViewCount(Long viewCount) {
		this.viewCount = viewCount;
	}	
}
