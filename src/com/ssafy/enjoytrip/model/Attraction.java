package com.ssafy.enjoytrip.model;

/**
 * 관광 정보 한 건. TourAPI areaBasedList2 응답의 필요한 항목만 담는다.
 * latitude 는 mapy, longitude 는 mapx 에서 온다.
 */
public record Attraction(
        int contentId,
        int contentTypeId,
        String title,
        String addr1,
        String addr2,
        String zipcode,
        String tel,
        String firstImage,
        String firstImage2,
        double latitude,
        double longitude,
        String ldongRegnCd,
        String ldongSignguCd,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3,
        String modifiedTime) {

    public boolean hasImage() {
        return firstImage != null && !firstImage.isEmpty();
    }
}
