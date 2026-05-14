-- -- Required for the Create Listing flow.
-- -- Seller-submitted items use PENDING_REVIEW so admin-home can show them in Pending Approval.
ALTER TABLE items
MODIFY COLUMN status ENUM('DRAFT', 'PENDING_REVIEW', 'AVAILABLE', 'IN_AUCTION', 'SOLD', 'REMOVED')
NOT NULL DEFAULT 'DRAFT';
