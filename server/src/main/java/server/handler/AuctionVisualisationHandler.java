package server.handler;

import server.common.ProtocolConstants;
import server.common.model.AuctionVisualisationDTO;
import server.service.AuctionVisualisationService;

public class AuctionVisualisationHandler {

  private final AuctionVisualisationService visualisationService = new AuctionVisualisationService();

  public String handle(String[] request, int userId) {

    if (request == null || request.length == 0 || !ProtocolConstants.GET_AUCTION_VISUALISATION.equals(request[0])) {
      return ResponseBuilder.auctionVisualisationFail("INVALID_COMMAND");
    }

    if (userId <= 0) {
      return ResponseBuilder.auctionVisualisationFail(ProtocolConstants.FAIL_NOT_LOGGED_IN);
    }
    if (request.length < 2) {
      return ResponseBuilder.auctionVisualisationFail(ProtocolConstants.FAIL_INVALID_FORMAT);
    }

    int auctionId;
    try {
      auctionId = Integer.parseInt(request[1]);
    } catch (NumberFormatException e) {
      return ResponseBuilder.auctionVisualisationFail(ProtocolConstants.FAIL_INVALID_FORMAT);
    }

    AuctionVisualisationDTO dto = visualisationService.getVisualisation(auctionId);
    if (dto == null) {
      return ResponseBuilder.auctionVisualisationFail("AUCTION_NOT_FOUND");
    }

    return ResponseBuilder.auctionVisualisationSuccess(dto);
  }
}